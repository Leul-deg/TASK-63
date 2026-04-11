package com.reslife.api.domain.messaging;

import com.reslife.api.security.ReslifeUserDetails;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for the messaging centre.
 *
 * <pre>
 * GET    /api/messages/threads                          — inbox
 * POST   /api/messages/threads                          — start new thread
 * GET    /api/messages/threads/{id}                     — thread + messages (auto-read)
 * POST   /api/messages/threads/{id}/messages            — send text / quick-reply
 * POST   /api/messages/threads/{id}/messages/image      — send image
 * GET    /api/messages/threads/{id}/poll?after=ISO8601  — poll for new messages
 * DELETE /api/messages/threads/{id}/messages/{msgId}   — soft-delete own message
 *
 * GET    /api/messages/quick-replies                    — list templates
 * GET    /api/messages/users?q=                         — search users for new thread
 * GET    /api/messages/images/{filename}                — serve message image
 *
 * GET    /api/messages/blocks                           — my blocked staff
 * POST   /api/messages/blocks/{staffUserId}             — block
 * DELETE /api/messages/blocks/{staffUserId}             — unblock
 *
 * POST   /api/messages/notices                          — system notice (staff+)
 * </pre>
 */
@RestController
@RequestMapping("/api/messages")
public class MessagingController {

    private static final String STAFF_ROLES =
            "hasAnyRole('ADMIN','HOUSING_ADMINISTRATOR','DIRECTOR'," +
            "'RESIDENT_DIRECTOR','RESIDENT_ASSISTANT','RESIDENCE_STAFF','STAFF')";

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    // ── Inbox ──────────────────────────────────────────────────────────────────

    @GetMapping("/threads")
    public List<ThreadSummaryResponse> inbox(
            @AuthenticationPrincipal ReslifeUserDetails principal,
            HttpSession session) {
        return messagingService.listThreads(principal.getUserId(), session.getId());
    }

    // ── Start thread ───────────────────────────────────────────────────────────

    @PostMapping("/threads")
    @ResponseStatus(HttpStatus.CREATED)
    public ThreadSummaryResponse createThread(
            @Valid @RequestBody CreateThreadRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return messagingService.createThread(principal.getUserId(), req);
    }

    // ── Thread detail ──────────────────────────────────────────────────────────

    @GetMapping("/threads/{id}")
    public ThreadDetailResponse getThread(
            @PathVariable UUID id,
            @AuthenticationPrincipal ReslifeUserDetails principal,
            HttpSession session) {
        return messagingService.getThread(id, principal.getUserId(), session.getId());
    }

    // ── Send text / quick-reply ────────────────────────────────────────────────

    @PostMapping("/threads/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return messagingService.sendMessage(id, principal.getUserId(), req);
    }

    // ── Send image ─────────────────────────────────────────────────────────────

    @PostMapping(value = "/threads/{id}/messages/image",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendImage(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal ReslifeUserDetails principal) throws IOException {
        return messagingService.sendImageMessage(id, principal.getUserId(), file);
    }

    // ── Poll ───────────────────────────────────────────────────────────────────

    @GetMapping("/threads/{id}/poll")
    public List<MessageResponse> poll(
            @PathVariable UUID id,
            @RequestParam String after,
            @AuthenticationPrincipal ReslifeUserDetails principal,
            HttpSession session) {
        Instant afterInstant = Instant.parse(after);
        return messagingService.pollMessages(id, principal.getUserId(), afterInstant, session.getId());
    }

    // ── Delete own message ─────────────────────────────────────────────────────

    @DeleteMapping("/threads/{id}/messages/{msgId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable UUID id,
            @PathVariable UUID msgId,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        messagingService.deleteMessage(msgId, principal.getUserId());
    }

    // ── Quick replies ──────────────────────────────────────────────────────────

    @GetMapping("/quick-replies")
    public List<QuickReplyResponse> quickReplies() {
        return messagingService.listQuickReplies();
    }

    // ── User search ────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public List<UserSummaryResponse> searchUsers(
            @RequestParam(defaultValue = "") String q,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return messagingService.searchUsers(q, principal.getUserId());
    }

    // ── Serve message images ───────────────────────────────────────────────────

    @GetMapping("/images/{filename}")
    public ResponseEntity<Resource> serveImage(
            @PathVariable String filename,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        // Basic sanity check — filename is UUID.ext, never user-supplied path
        if (filename.contains("/") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = messagingService.serveImageAsParticipant(filename, principal.getUserId());
        String ct = filename.toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(ct))
                .body(resource);
    }

    // ── Blocks ─────────────────────────────────────────────────────────────────

    @GetMapping("/blocks")
    public List<BlockedStaffResponse> listBlocks(
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        return messagingService.listBlocks(principal.getUserId());
    }

    @PostMapping("/blocks/{staffUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(
            @PathVariable UUID staffUserId,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        messagingService.blockStaff(principal.getUserId(), staffUserId);
    }

    @DeleteMapping("/blocks/{staffUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(
            @PathVariable UUID staffUserId,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        messagingService.unblockStaff(principal.getUserId(), staffUserId);
    }

    // ── System notices ─────────────────────────────────────────────────────────

    @PostMapping("/notices")
    @PreAuthorize(STAFF_ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendNotice(
            @Valid @RequestBody SendNoticeRequest req,
            @AuthenticationPrincipal ReslifeUserDetails principal) {
        messagingService.sendSystemNotice(principal.getUserId(), req);
    }
}
