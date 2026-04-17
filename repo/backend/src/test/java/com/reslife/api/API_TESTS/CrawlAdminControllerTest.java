package com.reslife.api.API_TESTS;

import com.reslife.api.admin.CrawlAdminController;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.crawler.*;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.user.*;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link CrawlAdminController}.
 *
 * <ul>
 *   <li>All fifteen endpoints require {@code ADMIN} or {@code HOUSING_ADMINISTRATOR}.</li>
 *   <li>Non-admin users receive 403.</li>
 *   <li>Unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = CrawlAdminController.class)
@Import(SecurityConfig.class)
class CrawlAdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private CrawlSourceService    sourceService;
    @MockBean private CrawlJobService       jobService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID ADMIN_ID   = UUID.randomUUID();
    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID  = UUID.randomUUID();
    private static final UUID JOB_ID     = UUID.randomUUID();

    private SourceResponse stubSource;
    private JobResponse    stubJob;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        stubSource = new SourceResponse(
                SOURCE_ID, "Test Source", "http://internal/site", "HTML", "desc",
                "Cape Town", null, null, null, null, 1000, 3, 100, true, null,
                Instant.now(), Instant.now());
        stubJob = new JobResponse(
                JOB_ID, SOURCE_ID, "Test Source", "MANUAL", "RUNNING",
                0, 0, 0, 0, Instant.now(), null, null, null, Instant.now());

        when(sourceService.listSources(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(stubSource)));
        when(sourceService.createSource(any(), any())).thenReturn(stubSource);
        when(sourceService.getSource(eq(SOURCE_ID))).thenReturn(stubSource);
        when(sourceService.updateSource(eq(SOURCE_ID), any())).thenReturn(stubSource);
        doNothing().when(sourceService).deleteSource(eq(SOURCE_ID));
        when(sourceService.triggerManual(eq(SOURCE_ID), any())).thenReturn(stubJob);

        when(jobService.listJobsBySource(eq(SOURCE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stubJob)));
        when(jobService.listJobs(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(stubJob)));
        when(jobService.getJob(eq(JOB_ID))).thenReturn(stubJob);
        when(jobService.pauseJob(eq(JOB_ID))).thenReturn(stubJob);
        when(jobService.resumeJob(eq(JOB_ID))).thenReturn(stubJob);
        when(jobService.cancelJob(eq(JOB_ID))).thenReturn(stubJob);
        when(jobService.listPages(eq(JOB_ID), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(jobService.engineStatus())
                .thenReturn(new CrawlEngineService.EngineStatus(5, 0, List.of()));
    }

    // ── Sources ────────────────────────────────────────────────────────────────

    @Test
    void admin_canListSources() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/sources")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(SOURCE_ID.toString()));
    }

    @Test
    void student_cannotListSources() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/sources")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotListSources() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/sources"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_canCreateSource() throws Exception {
        mockMvc.perform(post("/api/admin/crawl/sources")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Test Source","baseUrl":"http://internal/site",
                                 "siteType":"HTML","delayMsBetweenRequests":1000,
                                 "maxDepth":3,"maxPages":100,"active":true}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(SOURCE_ID.toString()));
    }

    @Test
    void admin_canGetSource() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/sources/{id}", SOURCE_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.name").value("Test Source"));
    }

    @Test
    void admin_canUpdateSource() throws Exception {
        mockMvc.perform(put("/api/admin/crawl/sources/{id}", SOURCE_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Updated Source","baseUrl":"http://internal/site",
                                 "siteType":"HTML","delayMsBetweenRequests":2000,
                                 "maxDepth":3,"maxPages":100,"active":true}
                                """))
               .andExpect(status().isOk());
    }

    @Test
    void admin_canDeleteSource() throws Exception {
        mockMvc.perform(delete("/api/admin/crawl/sources/{id}", SOURCE_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    @Test
    void admin_canTriggerManualCrawl() throws Exception {
        mockMvc.perform(post("/api/admin/crawl/sources/{id}/trigger", SOURCE_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(JOB_ID.toString()));
    }

    @Test
    void admin_canListJobsForSource() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/sources/{id}/jobs", SOURCE_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(JOB_ID.toString()));
    }

    // ── Jobs ───────────────────────────────────────────────────────────────────

    @Test
    void admin_canListAllJobs() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/jobs")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content[0].id").value(JOB_ID.toString()));
    }

    @Test
    void admin_canGetJob() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/jobs/{id}", JOB_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    void admin_canPauseJob() throws Exception {
        mockMvc.perform(post("/api/admin/crawl/jobs/{id}/pause", JOB_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isOk());
    }

    @Test
    void admin_canResumeJob() throws Exception {
        mockMvc.perform(post("/api/admin/crawl/jobs/{id}/resume", JOB_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isOk());
    }

    @Test
    void admin_canCancelJob() throws Exception {
        mockMvc.perform(post("/api/admin/crawl/jobs/{id}/cancel", JOB_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isOk());
    }

    @Test
    void admin_canListPagesForJob() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/jobs/{id}/pages", JOB_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.content").isArray());
    }

    // ── Engine status ──────────────────────────────────────────────────────────

    @Test
    void admin_canGetEngineStatus() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/engine/status")
                        .with(asUser(ADMIN_ID, RoleName.ADMIN)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.maxConcurrent").value(5))
               .andExpect(jsonPath("$.activeWorkers").value(0));
    }

    @Test
    void student_cannotGetEngineStatus() throws Exception {
        mockMvc.perform(get("/api/admin/crawl/engine/status")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static RequestPostProcessor asUser(UUID userId, RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("user");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        ReslifeUserDetails details = ReslifeUserDetails.from(user);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities()));
    }
}
