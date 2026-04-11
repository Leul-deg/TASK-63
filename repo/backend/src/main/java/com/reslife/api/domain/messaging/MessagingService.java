package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.storage.StorageService;
import org.springframework.core.io.Resource;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Core business logic for the messaging centre.
 *
 * <h3>Read-state model</h3>
 * <p>When a participant opens a thread ({@link #getThread}), read receipts are
 * inserted for every visible message. Background polling
 * ({@link #pollMessages}) inserts delivery receipts so status can advance from
 * SENT to DELIVERED before the recipient opens the thread. The composite key
 * {@code (message_id, reader_user_id, session_id)} gives per-device tracking:
 * reading on one device does not mark messages as read on another.
 *
 * <h3>Block rules</h3>
 * <ul>
 *   <li>A student may block a staff user from initiating new {@link ThreadType#DIRECT} threads.</li>
 *   <li>Staff can always send {@link ThreadType#SYSTEM_NOTICE} messages, bypassing all blocks.</li>
 *   <li>Blocks do not prevent replies within an already-open thread.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MessagingService {

    private static final Logger log = LoggerFactory.getLogger(MessagingService.class);

    private static final Pattern JPEG_PNG = Pattern.compile("image/(jpeg|png)", Pattern.CASE_INSENSITIVE);

    /** Roles that a student is permitted to block from initiating new threads. */
    private static final Set<RoleName> BLOCKABLE_STAFF_ROLES = Set.of(
            RoleName.ADMIN, RoleName.HOUSING_ADMINISTRATOR, RoleName.DIRECTOR,
            RoleName.RESIDENT_DIRECTOR, RoleName.RESIDENT_ASSISTANT,
            RoleName.RESIDENCE_STAFF, RoleName.STAFF
    );
    private static final long   MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    private static final String IMG_BASE = "/api/messages/images";

    private final MessageThreadRepository        threadRepo;
    private final MessageRepository              messageRepo;
    private final MessageReadReceiptRepository   receiptRepo;
    private final MessageDeliveryReceiptRepository deliveryReceiptRepo;
    private final StaffBlockRepository           blockRepo;
    private final QuickReplyTemplateRepository   qrRepo;
    private final UserRepository                 userRepo;
    private final StorageService                 storageService;

    public MessagingService(MessageThreadRepository threadRepo,
                            MessageRepository messageRepo,
                            MessageReadReceiptRepository receiptRepo,
                            MessageDeliveryReceiptRepository deliveryReceiptRepo,
                            StaffBlockRepository blockRepo,
                            QuickReplyTemplateRepository qrRepo,
                            UserRepository userRepo,
                            StorageService storageService) {
        this.threadRepo          = threadRepo;
        this.messageRepo         = messageRepo;
        this.receiptRepo         = receiptRepo;
        this.deliveryReceiptRepo = deliveryReceiptRepo;
        this.blockRepo           = blockRepo;
        this.qrRepo              = qrRepo;
        this.userRepo            = userRepo;
        this.storageService      = storageService;
    }

    // ── Inbox ──────────────────────────────────────────────────────────────────

    /**
     * Returns all threads the user participates in, newest-first,
     * with per-session unread counts.
     */
    public List<ThreadSummaryResponse> listThreads(UUID userId, String sessionId) {
        return threadRepo.findByParticipantUserId(userId).stream()
                .map(t -> toSummary(t, userId, sessionId))
                .collect(Collectors.toList());
    }

    // ── Thread detail ──────────────────────────────────────────────────────────

    /**
     * Returns full thread + messages and auto-marks every message as read
     * for this user+session.
     */
    @Transactional
    public ThreadDetailResponse getThread(UUID threadId, UUID requesterId, String sessionId) {
        MessageThread thread = requireThread(threadId);
        requireParticipant(thread, requesterId);

        List<Message> messages = messageRepo.findByThreadIdOrderByCreatedAtAsc(threadId);
        batchMarkRead(messages, requesterId, sessionId);

        Map<UUID, Long> readCounts     = readCountsBySender(messages, requesterId);
        Map<UUID, Long> deliveryCounts = deliveryCountsBySender(messages, requesterId);
        List<MessageResponse> msgResponses = messages.stream()
                .map(m -> toMessageResponse(m, requesterId, readCounts, deliveryCounts))
                .collect(Collectors.toList());

        return new ThreadDetailResponse(
                thread.getId(), thread.getSubject(), thread.getThreadType().name(),
                participantInfos(thread), msgResponses, thread.getUpdatedAt()
        );
    }

    // ── Create thread ──────────────────────────────────────────────────────────

    /**
     * Creates a DIRECT thread.
     *
     * @throws BlockedException if any recipient has blocked the sender
     */
    @Transactional
    public ThreadSummaryResponse createThread(UUID senderId, CreateThreadRequest req) {
        User sender = requireUser(senderId);
        if (hasRoleName(sender, RoleName.STUDENT)) {
            List<User> recipients = req.recipientIds().stream()
                    .filter(id -> !id.equals(senderId))
                    .map(this::requireUser)
                    .toList();
            boolean allRecipientsAreStaff = !recipients.isEmpty()
                    && recipients.stream().allMatch(this::isBlockableStaff);
            if (!allRecipientsAreStaff) {
                throw new IllegalArgumentException(
                        "Students may only start new conversations with staff members.");
            }
        }
        if (blockRepo.anyRecipientBlocksStaff(req.recipientIds(), senderId)) {
            throw new BlockedException(
                    "One or more recipients have blocked you from starting new conversations.");
        }
        return buildThread(senderId, req.subject(), ThreadType.DIRECT, req.body(), req.recipientIds());
    }

    // ── Send messages ──────────────────────────────────────────────────────────

    /** Sends a text message or expands a quick-reply template. */
    @Transactional
    public MessageResponse sendMessage(UUID threadId, UUID senderId, SendMessageRequest req) {
        if ((req.body() == null || req.body().isBlank())
                && (req.quickReplyKey() == null || req.quickReplyKey().isBlank())) {
            throw new IllegalArgumentException("Message must have a body or a quickReplyKey.");
        }
        MessageThread thread = requireThread(threadId);
        requireParticipant(thread, senderId);
        User sender = requireUser(senderId);

        Message msg = new Message();
        msg.setThread(thread);
        msg.setSender(sender);

        if (req.quickReplyKey() != null && !req.quickReplyKey().isBlank()) {
            QuickReplyTemplate tpl = qrRepo.findByActiveTrueOrderBySortOrderAsc().stream()
                    .filter(t -> t.getReplyKey().equals(req.quickReplyKey()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown quick-reply key: " + req.quickReplyKey()));
            msg.setMessageType(MessageType.QUICK_REPLY);
            msg.setBody(tpl.getBody());
            msg.setQuickReplyKey(tpl.getReplyKey());
        } else {
            msg.setMessageType(MessageType.TEXT);
            msg.setBody(req.body());
        }

        messageRepo.save(msg);
        touchThread(thread);
        return toMessageResponse(msg, senderId, Collections.emptyMap(), Collections.emptyMap());
    }

    /** Validates, stores, and records an image message. Accepts JPEG/PNG ≤ 15 MB. */
    @Transactional
    public MessageResponse sendImageMessage(UUID threadId, UUID senderId,
                                            MultipartFile file) throws IOException {
        validateImage(file);
        MessageThread thread = requireThread(threadId);
        requireParticipant(thread, senderId);
        User sender = requireUser(senderId);

        String ext = getExtension(Objects.requireNonNull(file.getOriginalFilename()));
        String storedFilename = UUID.randomUUID() + "." + ext;
        storageService.storeMessageImage(storedFilename, file);

        Message msg = new Message();
        msg.setThread(thread);
        msg.setSender(sender);
        msg.setMessageType(MessageType.IMAGE);
        msg.setImageFilename(storedFilename);
        messageRepo.save(msg);
        touchThread(thread);
        return toMessageResponse(msg, senderId, Collections.emptyMap(), Collections.emptyMap());
    }

    // ── Polling ────────────────────────────────────────────────────────────────

    /**
     * Returns messages created after {@code after} and marks them as read
     * for this user+session.  Safe to call every few seconds.
     */
    @Transactional
    public List<MessageResponse> pollMessages(UUID threadId, UUID userId,
                                              Instant after, String sessionId) {
        MessageThread thread = requireThread(threadId);
        requireParticipant(thread, userId);

        List<Message> msgs = messageRepo.findByThreadIdAfter(threadId, after);
        batchMarkDelivered(msgs, userId);

        Map<UUID, Long> readCounts     = readCountsBySender(msgs, userId);
        Map<UUID, Long> deliveryCounts = deliveryCountsBySender(msgs, userId);
        return msgs.stream()
                .map(m -> toMessageResponse(m, userId, readCounts, deliveryCounts))
                .collect(Collectors.toList());
    }

    // ── Delete own message ─────────────────────────────────────────────────────

    @Transactional
    public void deleteMessage(UUID messageId, UUID requesterId) {
        Message msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("Message not found: " + messageId));
        if (msg.getSender() == null || !msg.getSender().getId().equals(requesterId)) {
            throw new IllegalArgumentException("You can only delete your own messages.");
        }
        msg.softDelete();
        messageRepo.save(msg);
        if (msg.getImageFilename() != null) {
            storageService.deleteMessageImage(msg.getImageFilename());
        }
    }

    // ── System notices ─────────────────────────────────────────────────────────

    /** Staff-only: creates a SYSTEM_NOTICE thread to one or more recipients, bypassing blocks. */
    @Transactional
    public void sendSystemNotice(UUID staffId, SendNoticeRequest req) {
        buildThread(staffId, req.subject(), ThreadType.SYSTEM_NOTICE,
                req.body(), req.recipientIds());
    }

    // ── Image serving ─────────────────────────────────────────────────────────

    /**
     * Loads a message image, gated on thread-participant membership.
     *
     * @throws EntityNotFoundException if no message owns {@code filename}
     * @throws BlockedException        if the requester is not a thread participant (→ 403)
     */
    public Resource serveImageAsParticipant(String filename, UUID requesterId) {
        Message msg = messageRepo.findByImageFilename(filename)
                .orElseThrow(() -> new EntityNotFoundException("Image not found: " + filename));
        requireParticipant(msg.getThread(), requesterId);
        return storageService.loadMessageImage(filename);
    }

    // ── Quick replies ──────────────────────────────────────────────────────────

    public List<QuickReplyResponse> listQuickReplies() {
        return qrRepo.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(QuickReplyResponse::from)
                .collect(Collectors.toList());
    }

    // ── User search ────────────────────────────────────────────────────────────

    public List<UserSummaryResponse> searchUsers(String q, UUID currentUserId) {
        if (q == null || q.isBlank()) return Collections.emptyList();
        User actor = requireUser(currentUserId);
        List<User> matches = hasRoleName(actor, RoleName.STUDENT)
                ? userRepo.searchUsersByRoleNames(
                        q.strip(), currentUserId, BLOCKABLE_STAFF_ROLES, PageRequest.of(0, 20))
                : userRepo.searchUsers(q.strip(), currentUserId, PageRequest.of(0, 20));
        return matches.stream().map(UserSummaryResponse::from).collect(Collectors.toList());
    }

    // ── Blocks ─────────────────────────────────────────────────────────────────

    public List<BlockedStaffResponse> listBlocks(UUID studentId) {
        return blockRepo.findByIdStudentUserId(studentId).stream()
                .map(b -> {
                    UUID staffId = b.getId().getBlockedStaffUserId();
                    String name = userRepo.findById(staffId)
                            .map(MessagingService::displayName).orElse("Unknown");
                    return new BlockedStaffResponse(staffId, name, b.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void blockStaff(UUID studentId, UUID staffId) {
        User actor = requireUser(studentId);
        if (!hasRoleName(actor, RoleName.STUDENT)) {
            throw new IllegalArgumentException("Only students may block staff members.");
        }
        User target = requireUser(staffId);
        if (!isBlockableStaff(target)) {
            throw new IllegalArgumentException("You may only block staff members.");
        }
        if (!blockRepo.existsByIdStudentUserIdAndIdBlockedStaffUserId(studentId, staffId)) {
            blockRepo.save(new StaffBlock(studentId, staffId));
        }
    }

    @Transactional
    public void unblockStaff(UUID studentId, UUID staffId) {
        User actor = requireUser(studentId);
        if (!hasRoleName(actor, RoleName.STUDENT)) {
            throw new IllegalArgumentException("Only students may unblock staff members.");
        }
        User target = requireUser(staffId);
        if (!isBlockableStaff(target)) {
            throw new IllegalArgumentException("You may only unblock staff members.");
        }
        StaffBlockId key = new StaffBlockId(studentId, staffId);
        if (blockRepo.existsById(key)) {
            blockRepo.deleteById(key);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private ThreadSummaryResponse buildThread(UUID creatorId, String subject,
                                              ThreadType type, String body,
                                              List<UUID> recipientIds) {
        User creator = requireUser(creatorId);
        MessageThread thread = new MessageThread();
        thread.setSubject(subject);
        thread.setThreadType(type);
        thread.setCreatedBy(creator);
        threadRepo.save(thread); // persist first to get the ID for the composite key

        addParticipant(thread, creator);
        for (UUID rid : recipientIds) {
            if (!rid.equals(creatorId)) addParticipant(thread, requireUser(rid));
        }
        threadRepo.save(thread); // flush participants via cascade

        Message msg = new Message();
        msg.setThread(thread);
        msg.setSender(creator);
        msg.setBody(body);
        msg.setMessageType(type == ThreadType.SYSTEM_NOTICE
                ? MessageType.SYSTEM_NOTICE : MessageType.TEXT);
        messageRepo.save(msg);
        touchThread(thread);

        return toSummary(thread, creatorId, null);
    }

    private void addParticipant(MessageThread thread, User user) {
        thread.getParticipants().add(new MessageThreadParticipant(thread, user));
    }

    private void touchThread(MessageThread thread) {
        threadRepo.touchUpdatedAt(thread.getId(), Instant.now());
    }

    private void batchMarkRead(List<Message> messages, UUID userId, String sessionId) {
        if (messages.isEmpty() || sessionId == null) return;
        List<UUID> ids = messages.stream().map(Message::getId).collect(Collectors.toList());
        Set<UUID> already = receiptRepo.findReadMessageIds(ids, userId, sessionId);
        List<MessageReadReceipt> toSave = new ArrayList<>();
        for (Message m : messages) {
            if (m.getSender() != null && m.getSender().getId().equals(userId)) continue;
            if (!already.contains(m.getId()))
                toSave.add(new MessageReadReceipt(m.getId(), userId, sessionId));
        }
        if (!toSave.isEmpty()) receiptRepo.saveAll(toSave);
    }

    /**
     * Inserts a delivery receipt for each message the user did not send and
     * has not already been marked delivered.  Called by {@link #pollMessages}
     * so that background polls advance status from SENT → DELIVERED without
     * the recipient needing to open the thread.
     */
    private void batchMarkDelivered(List<Message> messages, UUID userId) {
        if (messages.isEmpty()) return;
        List<UUID> ids = messages.stream().map(Message::getId).collect(Collectors.toList());
        Set<UUID> already = deliveryReceiptRepo.findDeliveredMessageIds(ids, userId);
        List<MessageDeliveryReceipt> toSave = new ArrayList<>();
        for (Message m : messages) {
            if (m.getSender() != null && m.getSender().getId().equals(userId)) continue;
            if (!already.contains(m.getId()))
                toSave.add(new MessageDeliveryReceipt(m.getId(), userId));
        }
        if (!toSave.isEmpty()) deliveryReceiptRepo.saveAll(toSave);
    }

    /** Returns map of messageId → count of distinct recipients who have a delivery receipt. */
    private Map<UUID, Long> deliveryCountsBySender(List<Message> messages, UUID senderId) {
        List<UUID> myIds = messages.stream()
                .filter(m -> m.getSender() != null && m.getSender().getId().equals(senderId))
                .map(Message::getId).collect(Collectors.toList());
        if (myIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : deliveryReceiptRepo.countDeliveriesByMessageExcludingSender(myIds, senderId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    /** Returns map of messageId → count of distinct recipients who read it. */
    private Map<UUID, Long> readCountsBySender(List<Message> messages, UUID viewerId) {
        List<UUID> myIds = messages.stream()
                .filter(m -> m.getSender() != null && m.getSender().getId().equals(viewerId))
                .map(Message::getId).collect(Collectors.toList());
        if (myIds.isEmpty()) return Collections.emptyMap();
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : receiptRepo.countReadsByMessageExcludingSender(myIds, viewerId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private MessageResponse toMessageResponse(Message m, UUID viewerId,
                                              Map<UUID, Long> readCounts,
                                              Map<UUID, Long> deliveryCounts) {
        String status = null;
        if (m.getSender() != null && m.getSender().getId().equals(viewerId)) {
            if (readCounts.getOrDefault(m.getId(), 0L) > 0) {
                status = "READ";
            } else if (deliveryCounts.getOrDefault(m.getId(), 0L) > 0) {
                status = "DELIVERED";
            } else {
                status = "SENT";
            }
        }
        return MessageResponse.from(m, IMG_BASE, status,
                m.getSender() != null ? displayName(m.getSender()) : "System");
    }

    private ThreadSummaryResponse toSummary(MessageThread t, UUID userId, String sessionId) {
        Optional<Message> last = messageRepo.findLastInThread(t.getId());
        MessageResponse lastMsg = last.map(m -> toMessageResponse(m, userId,
                                              Collections.emptyMap(), Collections.emptyMap()))
                                      .orElse(null);
        long unread = sessionId != null
                ? receiptRepo.countUnread(t.getId(), userId, sessionId)
                : 0L;
        return new ThreadSummaryResponse(t.getId(), t.getSubject(), t.getThreadType().name(),
                participantInfos(t), lastMsg, unread, t.getUpdatedAt());
    }

    private List<ParticipantInfo> participantInfos(MessageThread t) {
        return t.getParticipants().stream()
                .map(p -> ParticipantInfo.from(p.getUser()))
                .collect(Collectors.toList());
    }

    private MessageThread requireThread(UUID id) {
        return threadRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Thread not found: " + id));
    }

    private User requireUser(UUID id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + id));
    }

    private void requireParticipant(MessageThread thread, UUID userId) {
        boolean member = thread.getParticipants().stream()
                .anyMatch(p -> p.getId().getUserId().equals(userId));
        if (!member) throw new BlockedException("You are not a participant in this thread.");
    }

    private static String displayName(User u) {
        return (u.getFirstName() != null && u.getLastName() != null)
                ? u.getFirstName() + " " + u.getLastName()
                : u.getUsername();
    }

    private boolean hasRoleName(User user, RoleName roleName) {
        return user.getUserRoles().stream()
                .anyMatch(ur -> ur.getRole().getName() == roleName);
    }

    private boolean isBlockableStaff(User user) {
        return user.getUserRoles().stream()
                .anyMatch(ur -> BLOCKABLE_STAFF_ROLES.contains(ur.getRole().getName()));
    }

    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Uploaded file is empty.");
        if (file.getSize() > MAX_IMAGE_BYTES)
            throw new IllegalArgumentException("Image exceeds the 15 MB size limit.");
        String ct = file.getContentType();
        if (ct == null || !JPEG_PNG.matcher(ct).matches())
            throw new IllegalArgumentException("Only JPEG and PNG images are accepted.");
        String name = file.getOriginalFilename();
        if (name == null || (!name.toLowerCase().endsWith(".jpg")
                && !name.toLowerCase().endsWith(".jpeg")
                && !name.toLowerCase().endsWith(".png")))
            throw new IllegalArgumentException("File extension must be .jpg, .jpeg, or .png.");
    }

    private static String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "bin";
    }
}
