package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.housing.*;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.user.*;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import com.reslife.api.storage.AttachmentService;
import com.reslife.api.storage.StorageService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;
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
 * HTTP-layer tests for {@link HousingController}.
 *
 * <ul>
 *   <li>All six endpoints require a staff/admin role.</li>
 *   <li>Students receive 403; unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = HousingController.class)
@Import(SecurityConfig.class)
class HousingControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private HousingService                housingService;
    @MockBean private ResidentService               residentService;
    @MockBean private AgreementAttachmentRepository attachmentRepository;
    @MockBean private AttachmentService             attachmentService;
    @MockBean private StorageService                storageService;
    @MockBean private UserDetailsServiceImpl        userDetailsService;
    @MockBean private IntegrationAuthFilter         integrationAuthFilter;
    @MockBean private UserRepository                userRepository;

    private static final UUID STAFF_ID      = UUID.randomUUID();
    private static final UUID STUDENT_ID    = UUID.randomUUID();
    private static final UUID RESIDENT_ID   = UUID.randomUUID();
    private static final UUID AGREEMENT_ID  = UUID.randomUUID();
    private static final UUID ATTACHMENT_ID = UUID.randomUUID();

    private HousingAgreement    stubAgreement;
    private AgreementAttachment stubAttachment;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(new User()));

        // Resident stub
        Resident stubResident = mock(Resident.class);
        when(stubResident.getId()).thenReturn(RESIDENT_ID);
        when(residentService.findById(eq(RESIDENT_ID))).thenReturn(stubResident);

        // Agreement stub — uses reflection for BaseEntity.id
        stubAgreement = mock(HousingAgreement.class);
        when(stubAgreement.getId()).thenReturn(AGREEMENT_ID);
        when(stubAgreement.getResident()).thenReturn(stubResident);
        when(stubAgreement.getAgreementType()).thenReturn("STANDARD");
        when(stubAgreement.getSignedDate()).thenReturn(LocalDate.now());
        when(stubAgreement.getExpiresDate()).thenReturn(LocalDate.now().plusYears(1));
        when(stubAgreement.getStatus()).thenReturn(AgreementStatus.SIGNED);
        when(stubAgreement.getVersion()).thenReturn("1.0");
        when(stubAgreement.getNotes()).thenReturn(null);

        when(housingService.findAgreementsByResident(eq(RESIDENT_ID)))
                .thenReturn(List.of(stubAgreement));
        when(housingService.createAgreement(eq(RESIDENT_ID), any(HousingAgreement.class)))
                .thenReturn(stubAgreement);
        when(attachmentRepository.countByAgreementId(eq(AGREEMENT_ID))).thenReturn(0L);

        // Attachment stub
        stubAttachment = mock(AgreementAttachment.class);
        when(stubAttachment.getId()).thenReturn(ATTACHMENT_ID);
        when(stubAttachment.getAgreement()).thenReturn(stubAgreement);
        when(stubAttachment.getOriginalFilename()).thenReturn("test.pdf");
        when(stubAttachment.getStoredFilename()).thenReturn("uuid-stored.pdf");
        when(stubAttachment.getContentType()).thenReturn("application/pdf");
        when(stubAttachment.getFileSizeBytes()).thenReturn(1024L);
        when(stubAttachment.getUploadedBy()).thenReturn("staff");
        when(stubAttachment.getCreatedAt()).thenReturn(Instant.now());

        when(attachmentService.findByAgreement(eq(AGREEMENT_ID)))
                .thenReturn(List.of(stubAttachment));
        when(attachmentService.upload(eq(AGREEMENT_ID), any(), any()))
                .thenReturn(stubAttachment);
        when(attachmentService.findById(eq(ATTACHMENT_ID))).thenReturn(stubAttachment);
        when(storageService.load(eq(AGREEMENT_ID), any()))
                .thenReturn(new ByteArrayResource("file-content".getBytes()));
        doNothing().when(attachmentService).delete(eq(ATTACHMENT_ID));
    }

    // ── GET /api/residents/{rId}/agreements ───────────────────────────────────

    @Test
    void staff_canListAgreements() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(AGREEMENT_ID.toString()));
    }

    @Test
    void student_cannotListAgreements() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements", RESIDENT_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotListAgreements() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements", RESIDENT_ID))
               .andExpect(status().isUnauthorized());
    }

    // ── POST /api/residents/{rId}/agreements ──────────────────────────────────

    @Test
    void staff_canCreateAgreement() throws Exception {
        mockMvc.perform(post("/api/residents/{rId}/agreements", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agreementType":"STANDARD","status":"PENDING"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(AGREEMENT_ID.toString()));
    }

    @Test
    void student_cannotCreateAgreement() throws Exception {
        mockMvc.perform(post("/api/residents/{rId}/agreements", RESIDENT_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agreementType":"STANDARD"}
                                """))
               .andExpect(status().isForbidden());
    }

    // ── GET /api/residents/{rId}/agreements/{aId}/attachments ─────────────────

    @Test
    void staff_canListAttachments() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements/{aId}/attachments",
                            RESIDENT_ID, AGREEMENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value(ATTACHMENT_ID.toString()));
    }

    @Test
    void student_cannotListAttachments() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements/{aId}/attachments",
                            RESIDENT_ID, AGREEMENT_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── POST /api/residents/{rId}/agreements/{aId}/attachments (multipart) ────

    @Test
    void staff_canUploadAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/residents/{rId}/agreements/{aId}/attachments",
                                  RESIDENT_ID, AGREEMENT_ID)
                        .file(file)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(ATTACHMENT_ID.toString()));
    }

    @Test
    void student_cannotUploadAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "pdf-bytes".getBytes());

        mockMvc.perform(multipart("/api/residents/{rId}/agreements/{aId}/attachments",
                                  RESIDENT_ID, AGREEMENT_ID)
                        .file(file)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isForbidden());
    }

    // ── GET .../attachments/{id}/content ──────────────────────────────────────

    @Test
    void staff_canDownloadAttachment() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements/{aId}/attachments/{id}/content",
                            RESIDENT_ID, AGREEMENT_ID, ATTACHMENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Disposition",
                       org.hamcrest.Matchers.containsString("test.pdf")));
    }

    @Test
    void student_cannotDownloadAttachment() throws Exception {
        mockMvc.perform(get("/api/residents/{rId}/agreements/{aId}/attachments/{id}/content",
                            RESIDENT_ID, AGREEMENT_ID, ATTACHMENT_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── DELETE .../attachments/{id} ───────────────────────────────────────────

    @Test
    void staff_canDeleteAttachment() throws Exception {
        mockMvc.perform(delete("/api/residents/{rId}/agreements/{aId}/attachments/{id}",
                               RESIDENT_ID, AGREEMENT_ID, ATTACHMENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    @Test
    void student_cannotDeleteAttachment() throws Exception {
        mockMvc.perform(delete("/api/residents/{rId}/agreements/{aId}/attachments/{id}",
                               RESIDENT_ID, AGREEMENT_ID, ATTACHMENT_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf()))
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
