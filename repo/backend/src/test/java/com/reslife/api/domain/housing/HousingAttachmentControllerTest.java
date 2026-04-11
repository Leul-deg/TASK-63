package com.reslife.api.domain.housing;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.user.*;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import com.reslife.api.storage.AttachmentService;
import com.reslife.api.storage.StorageService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HousingController.class)
@Import(SecurityConfig.class)
class HousingAttachmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private HousingService housingService;
    @MockBean private ResidentService residentService;
    @MockBean private AgreementAttachmentRepository attachmentRepository;
    @MockBean private AttachmentService attachmentService;
    @MockBean private StorageService storageService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter integrationAuthFilter;
    @MockBean private UserRepository userRepository;

    private static final UUID RESIDENT_ID = UUID.randomUUID();
    private static final UUID AGREEMENT_ID = UUID.randomUUID();
    private static final UUID ATTACHMENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        HousingAgreement agreement = mock(HousingAgreement.class);
        when(agreement.getId()).thenReturn(AGREEMENT_ID);
        when(housingService.findAgreementsByResident(RESIDENT_ID)).thenReturn(List.of(agreement));

        AgreementAttachment attachment = mock(AgreementAttachment.class);
        when(attachment.getId()).thenReturn(ATTACHMENT_ID);
        when(attachmentService.upload(eq(AGREEMENT_ID), any(), any())).thenReturn(attachment);
        when(attachmentService.findById(ATTACHMENT_ID)).thenReturn(attachment);
        when(attachment.getAgreement()).thenReturn(agreement);
    }

    @Test
    void staff_canUploadAttachment_forOwnedAgreement() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dummy.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46});

        mockMvc.perform(multipart("/api/residents/{residentId}/agreements/{agreementId}/attachments", RESIDENT_ID, AGREEMENT_ID)
                        .file(file)
                        .with(asUser(RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isCreated());
    }

    @Test
    void wrongResidentAgreementScope_returnsNotFound() throws Exception {
        when(housingService.findAgreementsByResident(RESIDENT_ID)).thenReturn(List.of());

        mockMvc.perform(delete("/api/residents/{residentId}/agreements/{agreementId}/attachments/{attachmentId}",
                        RESIDENT_ID, AGREEMENT_ID, ATTACHMENT_ID)
                        .with(asUser(RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor asUser(RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("staff");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        ReslifeUserDetails details = ReslifeUserDetails.from(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
        return authentication(token);
    }
}
