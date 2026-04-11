package com.reslife.api.domain.messaging;

import com.reslife.api.storage.StorageService;
import com.reslife.api.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the SENT → DELIVERED → READ status lifecycle introduced by High #5.
 *
 * <ul>
 *   <li>No receipts → SENT</li>
 *   <li>Delivery receipt exists (recipient polled) but no read receipt → DELIVERED</li>
 *   <li>Read receipt exists (recipient opened thread) → READ</li>
 * </ul>
 *
 * Tests exercise {@link MessagingService#getThread} from the sender's perspective —
 * the method that computes and returns per-message status to the sender.
 */
class MessageStatusLifecycleTest {

    private final MessageThreadRepository          threadRepo          = mock(MessageThreadRepository.class);
    private final MessageRepository                messageRepo         = mock(MessageRepository.class);
    private final MessageReadReceiptRepository     receiptRepo         = mock(MessageReadReceiptRepository.class);
    private final MessageDeliveryReceiptRepository deliveryReceiptRepo = mock(MessageDeliveryReceiptRepository.class);
    private final StaffBlockRepository             blockRepo           = mock(StaffBlockRepository.class);
    private final QuickReplyTemplateRepository     qrRepo              = mock(QuickReplyTemplateRepository.class);
    private final StorageService                   storageService      = mock(StorageService.class);

    // UserRepository is not used by getThread, but MessagingService requires one via userRepo
    private final com.reslife.api.domain.user.UserRepository userRepo = mock(com.reslife.api.domain.user.UserRepository.class);

    private MessagingService service;

    private static final UUID   THREAD_ID  = UUID.randomUUID();
    private static final UUID   SENDER_ID  = UUID.randomUUID();
    private static final UUID   MSG_ID     = UUID.randomUUID();
    private static final String SESSION_ID = "test-session-001";

    @BeforeEach
    void setUp() {
        service = new MessagingService(
                threadRepo, messageRepo, receiptRepo, deliveryReceiptRepo,
                blockRepo, qrRepo, userRepo, storageService);

        MessageThread thread = buildThread();
        Message message = buildMessage(thread);
        when(threadRepo.findById(THREAD_ID)).thenReturn(Optional.of(thread));
        when(messageRepo.findByThreadIdOrderByCreatedAtAsc(THREAD_ID))
                .thenReturn(List.of(message));

        // batchMarkRead dedup — sender viewing own messages never inserts receipts for them
        when(receiptRepo.findReadMessageIds(anyList(), eq(SENDER_ID), any()))
                .thenReturn(Set.of());
    }

    // ── No receipts → SENT ────────────────────────────────────────────────────

    @Test
    void status_isSent_whenNoReceiptsExist() {
        when(receiptRepo.countReadsByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.of());
        when(deliveryReceiptRepo.countDeliveriesByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.of());

        String status = fetchSenderMessageStatus();

        assertEquals("SENT", status);
    }

    // ── Delivery receipt exists (background poll) → DELIVERED ─────────────────

    @Test
    void status_isDelivered_whenRecipientHasPolledButNotReadThread() {
        when(receiptRepo.countReadsByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.of());
        when(deliveryReceiptRepo.countDeliveriesByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.<Object[]>of(new Object[]{MSG_ID, 1L}));

        String status = fetchSenderMessageStatus();

        assertEquals("DELIVERED", status);
    }

    // ── Read receipt exists (thread opened) → READ ────────────────────────────

    @Test
    void status_isRead_whenRecipientHasOpenedThread() {
        when(receiptRepo.countReadsByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.<Object[]>of(new Object[]{MSG_ID, 1L}));
        when(deliveryReceiptRepo.countDeliveriesByMessageExcludingSender(anyList(), eq(SENDER_ID)))
                .thenReturn(List.of()); // delivery count doesn't matter — READ takes priority

        String status = fetchSenderMessageStatus();

        assertEquals("READ", status);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Calls getThread as the sender and extracts the status on their own message. */
    private String fetchSenderMessageStatus() {
        ThreadDetailResponse response = service.getThread(THREAD_ID, SENDER_ID, SESSION_ID);
        return response.messages().stream()
                .filter(m -> SENDER_ID.equals(m.senderId()))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private MessageThread buildThread() {
        MessageThreadParticipantId pid = mock(MessageThreadParticipantId.class);
        when(pid.getUserId()).thenReturn(SENDER_ID);

        User senderUser = mock(User.class);
        when(senderUser.getId()).thenReturn(SENDER_ID);
        when(senderUser.getUsername()).thenReturn("alice");
        when(senderUser.getFirstName()).thenReturn("Alice");
        when(senderUser.getLastName()).thenReturn("Smith");

        MessageThreadParticipant participant = mock(MessageThreadParticipant.class);
        when(participant.getId()).thenReturn(pid);
        when(participant.getUser()).thenReturn(senderUser);

        MessageThread thread = mock(MessageThread.class);
        when(thread.getId()).thenReturn(THREAD_ID);
        when(thread.getSubject()).thenReturn("Test Thread");
        when(thread.getThreadType()).thenReturn(ThreadType.DIRECT);
        when(thread.getParticipants()).thenReturn(List.of(participant));
        when(thread.getUpdatedAt()).thenReturn(Instant.now());

        return thread;
    }

    private Message buildMessage(MessageThread thread) {
        User sender = mock(User.class);
        when(sender.getId()).thenReturn(SENDER_ID);
        when(sender.getFirstName()).thenReturn("Alice");
        when(sender.getLastName()).thenReturn("Smith");

        Message msg = mock(Message.class);
        when(msg.getId()).thenReturn(MSG_ID);
        when(msg.getSender()).thenReturn(sender);
        when(msg.getThread()).thenReturn(thread);
        when(msg.getBody()).thenReturn("Hello");
        when(msg.getMessageType()).thenReturn(MessageType.TEXT);
        when(msg.getImageFilename()).thenReturn(null);
        when(msg.getQuickReplyKey()).thenReturn(null);
        when(msg.isDeleted()).thenReturn(false);
        when(msg.getCreatedAt()).thenReturn(Instant.now());

        return msg;
    }
}
