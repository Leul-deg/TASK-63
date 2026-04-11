package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import com.reslife.api.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies that block/unblock semantics are role-enforced (Medium #8):
 *
 * <ul>
 *   <li>Only a STUDENT may call block/unblock.</li>
 *   <li>The target must be a staff member (any BLOCKABLE_STAFF_ROLES member).</li>
 *   <li>Blocking another student is rejected even if the caller is a student.</li>
 * </ul>
 */
class BlockSemanticsTest {

    private final MessageThreadRepository          threadRepo          = mock(MessageThreadRepository.class);
    private final MessageRepository                messageRepo         = mock(MessageRepository.class);
    private final MessageReadReceiptRepository     receiptRepo         = mock(MessageReadReceiptRepository.class);
    private final MessageDeliveryReceiptRepository deliveryReceiptRepo = mock(MessageDeliveryReceiptRepository.class);
    private final StaffBlockRepository             blockRepo           = mock(StaffBlockRepository.class);
    private final QuickReplyTemplateRepository     qrRepo              = mock(QuickReplyTemplateRepository.class);
    private final UserRepository                   userRepo            = mock(UserRepository.class);
    private final StorageService                   storageService      = mock(StorageService.class);

    private MessagingService service;

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID STAFF_ID   = UUID.randomUUID();
    private static final UUID NON_STUDENT_ID = UUID.randomUUID();
    private static final UUID OTHER_STUDENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MessagingService(
                threadRepo, messageRepo, receiptRepo, deliveryReceiptRepo,
                blockRepo, qrRepo, userRepo, storageService);

        User studentUser = buildUser(RoleName.STUDENT);
        User staffUser = buildUser(RoleName.RESIDENCE_STAFF);
        User otherStudentUser = buildUser(RoleName.STUDENT);

        when(userRepo.findById(STUDENT_ID)).thenReturn(Optional.of(studentUser));
        when(userRepo.findById(STAFF_ID)).thenReturn(Optional.of(staffUser));
        when(userRepo.findById(NON_STUDENT_ID)).thenReturn(Optional.of(staffUser));
        when(userRepo.findById(OTHER_STUDENT_ID)).thenReturn(Optional.of(otherStudentUser));
        when(threadRepo.save(any(MessageThread.class))).thenAnswer(inv -> {
            MessageThread thread = inv.getArgument(0);
            if (thread.getId() == null) {
                assignId(thread);
            }
            return thread;
        });
        when(messageRepo.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepo.findLastInThread(any())).thenReturn(Optional.empty());

        when(blockRepo.existsByIdStudentUserIdAndIdBlockedStaffUserId(any(), any())).thenReturn(false);
        when(blockRepo.existsById(any())).thenReturn(true);
    }

    // ── blockStaff — positive ─────────────────────────────────────────────────

    @Test
    void student_canBlockStaff() {
        assertDoesNotThrow(() -> service.blockStaff(STUDENT_ID, STAFF_ID));
        verify(blockRepo).save(any(StaffBlock.class));
    }

    // ── blockStaff — wrong actor role ─────────────────────────────────────────

    @Test
    void nonStudent_cannotBlock() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.blockStaff(NON_STUDENT_ID, STAFF_ID));
        assertTrue(ex.getMessage().contains("Only students"));
        verify(blockRepo, never()).save(any());
    }

    // ── blockStaff — wrong target role ───────────────────────────────────────

    @Test
    void student_cannotBlockAnotherStudent() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.blockStaff(STUDENT_ID, OTHER_STUDENT_ID));
        assertTrue(ex.getMessage().contains("staff"));
        verify(blockRepo, never()).save(any());
    }

    // ── unblockStaff — positive ───────────────────────────────────────────────

    @Test
    void student_canUnblockStaff() {
        assertDoesNotThrow(() -> service.unblockStaff(STUDENT_ID, STAFF_ID));
        verify(blockRepo).deleteById(any(StaffBlockId.class));
    }

    // ── unblockStaff — wrong actor role ──────────────────────────────────────

    @Test
    void nonStudent_cannotUnblock() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.unblockStaff(NON_STUDENT_ID, STAFF_ID));
        assertTrue(ex.getMessage().contains("Only students"));
        verify(blockRepo, never()).deleteById(any());
    }

    @Test
    void blockedStaff_cannotStartNewDirectThread() {
        when(blockRepo.anyRecipientBlocksStaff(List.of(STUDENT_ID), NON_STUDENT_ID)).thenReturn(true);

        BlockedException ex = assertThrows(
                BlockedException.class,
                () -> service.createThread(
                        NON_STUDENT_ID,
                        new CreateThreadRequest("Hello", List.of(STUDENT_ID), "Checking in")));

        assertTrue(ex.getMessage().contains("blocked"));
    }

    @Test
    void student_cannotStartThreadWithAnotherStudent() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.createThread(
                        STUDENT_ID,
                        new CreateThreadRequest("Hi", List.of(OTHER_STUDENT_ID), "Hello there")));

        assertTrue(ex.getMessage().contains("staff"));
    }

    @Test
    void studentUserSearch_onlyReturnsStaffMatches() {
        User searchResult = buildUser(RoleName.RESIDENCE_STAFF);
        when(userRepo.searchUsersByRoleNames(any(), eq(STUDENT_ID), any(), any()))
                .thenReturn(List.of(searchResult));

        List<UserSummaryResponse> results = service.searchUsers("ja", STUDENT_ID);

        assertEquals(1, results.size());
        verify(userRepo, never()).searchUsers(any(), any(), any());
        verify(userRepo).searchUsersByRoleNames(any(), eq(STUDENT_ID), any(), any());
    }

    @Test
    void systemNotice_bypassesBlockCheck() {
        when(blockRepo.anyRecipientBlocksStaff(List.of(STUDENT_ID), NON_STUDENT_ID)).thenReturn(true);

        assertDoesNotThrow(() -> service.sendSystemNotice(
                NON_STUDENT_ID,
                new SendNoticeRequest("Policy notice", "Read this", List.of(STUDENT_ID))));

        verify(blockRepo, never()).anyRecipientBlocksStaff(any(), any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static User buildUser(RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);

        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        return user;
    }

    private static void assignId(MessageThread thread) {
        try {
            java.lang.reflect.Field idField = com.reslife.api.common.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(thread, UUID.randomUUID());
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
