package com.reslife.api.domain.resident;

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

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JPA-layer integration tests that run against an H2 in-memory database
 * (PostgreSQL compatibility mode).  These are the only tests in the suite
 * that exercise the JPA entity mapping → JDBC → SQL path without mocking the
 * repository layer.
 *
 * <p>H2's {@code MODE=PostgreSQL} maps the {@code jsonb} column definitions
 * used by {@link com.reslife.api.domain.system.AuditLog} to VARCHAR, and
 * supports the {@code SMALLINT} column on {@link Resident#classYear}.
 *
 * <p>{@link EncryptionService} is imported so that the JPA converters
 * ({@link com.reslife.api.encryption.LocalDateEncryptionConverter}) can
 * access the singleton instance via {@code EncryptionService.instance()}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_integ;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false"
})
@Import(EncryptionService.class)
class ResidentRepositoryIntegrationTest {

    @Autowired
    private ResidentRepository residentRepository;

    @Autowired
    private TestEntityManager em;

    // ── existsByEmail ─────────────────────────────────────────────────────────

    @Test
    void existsByEmail_returnsFalse_whenNoResidentWithThatEmail() {
        assertThat(residentRepository.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    void existsByEmail_returnsTrue_whenResidentExists() {
        Resident r = new Resident();
        r.setFirstName("Alice");
        r.setLastName("Smith");
        r.setEmail("alice@test.com");
        em.persistAndFlush(r);

        assertThat(residentRepository.existsByEmail("alice@test.com")).isTrue();
    }

    // ── existsByEmailIgnoreCaseAndIdNot ───────────────────────────────────────

    @Test
    void existsByEmailIgnoreCaseAndIdNot_returnsFalse_whenOnlyMatchIsTheExcludedId() {
        Resident r = new Resident();
        r.setFirstName("Bob");
        r.setLastName("Jones");
        r.setEmail("bob@test.com");
        Resident saved = em.persistAndFlush(r);

        // Excluding the resident's own ID → no other match exists
        assertThat(residentRepository.existsByEmailIgnoreCaseAndIdNot("bob@test.com", saved.getId()))
                .isFalse();
    }

    @Test
    void existsByEmailIgnoreCaseAndIdNot_returnsTrue_whenADifferentResidentHasThatEmail() {
        Resident r = new Resident();
        r.setFirstName("Carol");
        r.setLastName("Lee");
        r.setEmail("carol@test.com");
        em.persistAndFlush(r);

        // Different (non-existent) excluded ID → the existing Carol is still found
        assertThat(residentRepository.existsByEmailIgnoreCaseAndIdNot("carol@test.com", UUID.randomUUID()))
                .isTrue();
    }

    // ── findByStudentId ───────────────────────────────────────────────────────

    @Test
    void findByStudentId_returnsEmpty_whenStudentIdNotFound() {
        assertThat(residentRepository.findByStudentId("S-MISSING")).isEmpty();
    }

    @Test
    void findByStudentId_returnsResident_whenStudentIdMatches() {
        Resident r = new Resident();
        r.setFirstName("Dave");
        r.setLastName("Kim");
        r.setEmail("dave@test.com");
        r.setStudentId("S-456");
        em.persistAndFlush(r);

        Optional<Resident> found = residentRepository.findByStudentId("S-456");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("dave@test.com");
    }

    // ── findByFirstNameIgnoreCaseAndLastNameIgnoreCase ────────────────────────

    @Test
    void findByFirstNameAndLastName_matchesCaseInsensitively() {
        Resident r = new Resident();
        r.setFirstName("Emma");
        r.setLastName("Watson");
        r.setEmail("emma@test.com");
        em.persistAndFlush(r);

        assertThat(residentRepository
                .findByFirstNameIgnoreCaseAndLastNameIgnoreCase("EMMA", "WATSON"))
                .hasSize(1);
    }

    @Test
    void findByFirstNameAndLastName_returnsEmpty_whenNoMatch() {
        assertThat(residentRepository
                .findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Ghost", "User"))
                .isEmpty();
    }

    // ── AES-256-GCM date-of-birth round-trip ─────────────────────────────────

    @Test
    void dateOfBirth_encryptsToDatabaseAndDecryptsBackToJavaType() {
        Resident r = new Resident();
        r.setFirstName("Eve");
        r.setLastName("Park");
        r.setEmail("eve@test.com");
        r.setDateOfBirth(LocalDate.of(1990, 3, 15));
        Resident saved = em.persistAndFlush(r);
        em.clear(); // evict from first-level cache to force a real DB read

        Resident loaded = residentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 3, 15));
    }

    @Test
    void dateOfBirth_storesNullWithoutError() {
        Resident r = new Resident();
        r.setFirstName("Frank");
        r.setLastName("Dale");
        r.setEmail("frank@test.com");
        r.setDateOfBirth(null);
        Resident saved = em.persistAndFlush(r);
        em.clear();

        Resident loaded = residentRepository.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getDateOfBirth()).isNull();
    }

    // ── Auditing support ──────────────────────────────────────────────────────

    /**
     * Enables Spring Data JPA auditing (@CreatedDate / @LastModifiedDate) for
     * this test slice. {@code JpaConfig} in the main source set is excluded by
     * {@code @DataJpaTest}'s component filter, so we re-enable auditing here
     * via a minimal nested {@code @TestConfiguration}.
     */
    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {}
}
