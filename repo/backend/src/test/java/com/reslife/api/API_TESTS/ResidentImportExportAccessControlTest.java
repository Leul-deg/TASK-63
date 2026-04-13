package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.ImportCommitResponse;
import com.reslife.api.domain.resident.ImportPreviewResponse;
import com.reslife.api.domain.resident.ResidentImportExportController;
import com.reslife.api.domain.resident.ResidentImportExportService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.multipart.MultipartFile;

import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResidentImportExportController.class)
@Import(SecurityConfig.class)
class ResidentImportExportAccessControlTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private ResidentImportExportService importExportService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter integrationAuthFilter;
    @MockBean private UserRepository userRepository;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        when(importExportService.preview(any(MultipartFile.class)))
                .thenReturn(new ImportPreviewResponse(0, 0, 0, 0, List.of()));
        when(importExportService.commit(any()))
                .thenReturn(new ImportCommitResponse(0, 0, 0, 0, List.of()));
        doNothing().when(importExportService).exportToCsv(any(Writer.class));
    }

    @Test
    void student_isDeniedFromImportPreview() throws Exception {
        mockMvc.perform(multipart("/api/residents/import/preview")
                        .file("file", "studentId,firstName,lastName,email\n".getBytes())
                        .with(asUser(RoleName.STUDENT))
                        .with(csrf()))
               .andExpect(status().isForbidden());
    }

    @Test
    void student_isDeniedFromImportCommit() throws Exception {
        mockMvc.perform(post("/api/residents/import/commit")
                        .with(asUser(RoleName.STUDENT))
                        .with(csrf())
                        .contentType("application/json")
                        .content("""
                            {"rows":[{"rowNumber":1,"action":"CREATE","mergeTargetId":null,
                            "data":{"studentId":"S1","firstName":"Alex","lastName":"Chen","email":"alex@example.edu",
                            "phone":"555-123-4567","dateOfBirth":"2005-01-01","enrollmentStatus":"ENROLLED",
                            "department":"CS","classYear":"2027","roomNumber":"101","buildingName":"Maple Hall"}}]}
                            """))
               .andExpect(status().isForbidden());
    }

    @Test
    void student_isDeniedFromExport() throws Exception {
        mockMvc.perform(get("/api/residents/export.csv")
                        .with(asUser(RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    @Test
    void staff_canExport() throws Exception {
        mockMvc.perform(get("/api/residents/export.csv")
                        .with(asUser(RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk());
    }

    private static RequestPostProcessor asUser(RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);
        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(user.getUsername()).thenReturn("user");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        ReslifeUserDetails details = ReslifeUserDetails.from(user);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
        return authentication(token);
    }
}
