package com.reslife.api.domain.messaging;

import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.User;
import com.reslife.api.encryption.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer tests for {@link StaffBlockRepository}.
 *
 * <p>Validates the existence check, student-owned block list, and the
 * many-recipient block check that guards DIRECT thread creation.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_staffblock;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class StaffBlockRepositoryTest {

    @Autowired private StaffBlockRepository blockRepo;
    @Autowired private TestEntityManager    em;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User savedUser(String email, String username) {
        User u = new User();
        u.setEmail(email);
        u.setUsername(username);
        u.setPasswordHash("$2a$10$hash");
        u.setFirstName("F");
        u.setLastName("L");
        u.setAccountStatus(AccountStatus.ACTIVE);
        return em.persistAndFlush(u);
    }

    private StaffBlock block(User student, User staff) {
        StaffBlock b = new StaffBlock(student.getId(), staff.getId());
        return em.persistAndFlush(b);
    }

    // ── existsByIdStudentUserIdAndIdBlockedStaffUserId ────────────────────────

    @Test
    void existsBlock_returnsTrue_whenBlockExists() {
        User student = savedUser("student-sb@test.com", "student_sb");
        User staff   = savedUser("staff-sb@test.com",   "staff_sb");
        block(student, staff);

        assertThat(blockRepo.existsByIdStudentUserIdAndIdBlockedStaffUserId(
                student.getId(), staff.getId())).isTrue();
    }

    @Test
    void existsBlock_returnsFalse_whenNoBlockExists() {
        User student = savedUser("noblock-s@test.com", "noblock_s");
        User staff   = savedUser("noblock-r@test.com", "noblock_r");

        assertThat(blockRepo.existsByIdStudentUserIdAndIdBlockedStaffUserId(
                student.getId(), staff.getId())).isFalse();
    }

    @Test
    void existsBlock_returnsFalse_whenRolesAreReversed() {
        User student = savedUser("rev-s@test.com", "rev_student");
        User staff   = savedUser("rev-r@test.com", "rev_staff");
        block(student, staff);

        assertThat(blockRepo.existsByIdStudentUserIdAndIdBlockedStaffUserId(
                staff.getId(), student.getId())).isFalse();
    }

    // ── findByIdStudentUserId ─────────────────────────────────────────────────

    @Test
    void findByStudentId_returnsAllBlocksCreatedByStudent() {
        User student = savedUser("listblock-s@test.com", "listblock_s");
        User staff1  = savedUser("listblock-r1@test.com", "listblock_r1");
        User staff2  = savedUser("listblock-r2@test.com", "listblock_r2");
        block(student, staff1);
        block(student, staff2);

        List<StaffBlock> blocks = blockRepo.findByIdStudentUserId(student.getId());

        assertThat(blocks).hasSize(2);
        assertThat(blocks).allMatch(b -> b.getId().getStudentUserId().equals(student.getId()));
    }

    @Test
    void findByStudentId_returnsEmpty_whenStudentHasNoBlocks() {
        User student = savedUser("noblocks@test.com", "noblocks_user");

        assertThat(blockRepo.findByIdStudentUserId(student.getId())).isEmpty();
    }

    // ── anyRecipientBlocksStaff ───────────────────────────────────────────────

    @Test
    void anyRecipientBlocksStaff_returnsTrue_whenAtLeastOneRecipientBlocked() {
        User student1 = savedUser("any-s1@test.com", "any_s1");
        User student2 = savedUser("any-s2@test.com", "any_s2");
        User staff    = savedUser("any-r@test.com",  "any_staff");
        block(student1, staff);   // student1 blocks staff
        // student2 does NOT block staff

        boolean result = blockRepo.anyRecipientBlocksStaff(
                List.of(student1.getId(), student2.getId()), staff.getId());

        assertThat(result).isTrue();
    }

    @Test
    void anyRecipientBlocksStaff_returnsFalse_whenNoRecipientBlocked() {
        User student1 = savedUser("noany-s1@test.com", "noany_s1");
        User student2 = savedUser("noany-s2@test.com", "noany_s2");
        User staff    = savedUser("noany-r@test.com",  "noany_staff");
        // Neither student blocks staff

        boolean result = blockRepo.anyRecipientBlocksStaff(
                List.of(student1.getId(), student2.getId()), staff.getId());

        assertThat(result).isFalse();
    }

    @Test
    void anyRecipientBlocksStaff_returnsFalse_whenRecipientListIsEmpty() {
        User staff = savedUser("empty-r@test.com", "empty_staff");

        boolean result = blockRepo.anyRecipientBlocksStaff(List.of(), staff.getId());

        assertThat(result).isFalse();
    }

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
