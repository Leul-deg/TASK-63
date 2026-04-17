package com.reslife.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.domain.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests that boot the real Spring application against an
 * in-memory H2 database (PostgreSQL compatibility mode).
 *
 * <p>Unlike the {@code @WebMvcTest} suite (which mocks the service layer), these
 * tests exercise the complete controller → service → repository → SQL path without
 * any mocked beans, validating that all layers work together correctly.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>Public endpoints are accessible without authentication.</li>
 *   <li>Protected endpoints return 401 when unauthenticated.</li>
 *   <li>A real login call creates a session and returns user data from the DB.</li>
 *   <li>Wrong credentials are rejected without a session being established.</li>
 *   <li>An authenticated session grants access to staff-only endpoints and returns
 *       real paginated responses from the database.</li>
 *   <li>Logout invalidates the session, causing subsequent requests to return 401.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reslife_fullstack;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "app.encryption.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "spring.session.store-type=none",
    "spring.jpa.show-sql=false",
    "logging.level.org.springframework.security=WARN",
    "logging.level.org.hibernate.SQL=WARN"
})
class FullStackIntegrationTest {

    @Autowired private MockMvc            mockMvc;
    @Autowired private UserRepository     userRepository;
    @Autowired private RoleRepository     roleRepository;
    @Autowired private PasswordEncoder    passwordEncoder;
    @Autowired private ObjectMapper       objectMapper;

    /** Populated by test 11; consumed by test 12. */
    private UUID createdResidentId;

    private static final String USERNAME = "integ-staff";
    private static final String PASSWORD = "Integration@Test123";
    private static final String LOGIN_BODY =
            "{\"identifier\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}";

    // ── Test-user seed ─────────────────────────────────────────────────────────

    @BeforeAll
    void seedTestUser() {
        // Persist a RESIDENCE_STAFF role and an ACTIVE user for the full auth flow.
        Role staffRole = new Role(RoleName.RESIDENCE_STAFF, "Staff (integration test)");
        roleRepository.save(staffRole);

        User user = new User();
        user.setUsername(USERNAME);
        user.setEmail("integ-staff@reslife-test.internal");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFirstName("Integration");
        user.setLastName("Tester");
        user.setAccountStatus(AccountStatus.ACTIVE);
        User saved = userRepository.save(user);

        // Cascade the role assignment through the user's collection.
        UserRole userRole = new UserRole(saved, staffRole);
        saved.getUserRoles().add(userRole);
        userRepository.save(saved);
    }

    // ── Public endpoints ──────────────────────────────────────────────────────

    @Test @Order(1)
    void healthEndpoint_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());
    }

    @Test @Order(2)
    void csrfEndpoint_isPublicAndReturnsHeaderName() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").exists());
    }

    // ── Unauthenticated access ─────────────────────────────────────────────────

    @Test @Order(3)
    void meEndpoint_returns401_withoutSession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(4)
    void residentsEndpoint_returns401_withoutSession() throws Exception {
        mockMvc.perform(get("/api/residents"))
                .andExpect(status().isUnauthorized());
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test @Order(5)
    void login_withValidCredentials_returns200AndUsername() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME));
    }

    @Test @Order(6)
    void login_withWrongPassword_returns401() throws Exception {
        String badBody = "{\"identifier\":\"" + USERNAME + "\",\"password\":\"wrong\"}";
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(7)
    void login_withUnknownUser_returns401() throws Exception {
        String badBody = "{\"identifier\":\"nobody\",\"password\":\"any\"}";
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badBody))
                .andExpect(status().isUnauthorized());
    }

    // ── Authenticated session flows ────────────────────────────────────────────

    @Test @Order(8)
    void me_afterLogin_returnsAuthenticatedUserFromDatabase() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.email").value("integ-staff@reslife-test.internal"));
    }

    @Test @Order(9)
    void residentsEndpoint_withStaffSession_returnsEmptyPage() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // Empty DB → empty page; content must be an array and totalElements must be 0.
        mockMvc.perform(get("/api/residents").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test @Order(10)
    void logout_invalidatesSession_causingSubsequentMeToReturn401() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());

        // Same session is now invalid
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private MockHttpSession getStaffSession() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andReturn();
        return (MockHttpSession) r.getRequest().getSession();
    }

    // ── Resident CRUD (no-mock, real DB) ──────────────────────────────────────

    @Test @Order(11)
    void createResident_withStaffSession_returns201AndPersistsRecord() throws Exception {
        MockHttpSession session = getStaffSession();
        String body = """
                {"firstName":"Integration","lastName":"Resident","email":"integ.resident@test.internal"}
                """;

        MvcResult result = mockMvc.perform(post("/api/residents")
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.firstName").value("Integration"))
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        createdResidentId = UUID.fromString(node.get("id").asText());
    }

    @Test @Order(12)
    void getResidentById_withStaffSession_returnsCreatedResident() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/residents/" + createdResidentId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdResidentId.toString()))
                .andExpect(jsonPath("$.lastName").value("Resident"));
    }

    @Test @Order(13)
    void updateResident_withStaffSession_returns200() throws Exception {
        MockHttpSession session = getStaffSession();
        String updated = """
                {"firstName":"Updated","lastName":"Resident","email":"integ.resident@test.internal"}
                """;

        mockMvc.perform(put("/api/residents/" + createdResidentId)
                        .with(csrf())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Updated"));
    }

    // ── Notifications, Messages, Housing ─────────────────────────────────────

    @Test @Order(14)
    void notificationsEndpoint_withStaffSession_returnsPagedResponse() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/notifications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test @Order(15)
    void messageThreadsEndpoint_withStaffSession_returnsArray() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/messages/threads").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test @Order(16)
    void moveInRecordsEndpoint_withStaffSession_returnsPagedResponse() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/housing/move-in-records").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test @Order(17)
    void analyticsEndpoint_withStaffSession_returnsOk() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/admin/analytics/dashboard").session(session))
                .andExpect(status().isOk());
    }

    @Test @Order(18)
    void notificationPreferencesEndpoint_withStaffSession_returnsOk() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(get("/api/notifications/preferences").session(session))
                .andExpect(status().isOk());
    }

    // ── Role-based access control ─────────────────────────────────────────────

    @Test @Order(19)
    void adminUsersEndpoint_withStaffSession_returns403() throws Exception {
        MockHttpSession session = getStaffSession();

        // RESIDENCE_STAFF does not have ADMIN privileges
        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden());
    }

    @Test @Order(20)
    void deleteResident_withStaffSession_returns204() throws Exception {
        MockHttpSession session = getStaffSession();

        mockMvc.perform(delete("/api/residents/" + createdResidentId)
                        .with(csrf())
                        .session(session))
                .andExpect(status().isNoContent());
    }
}
