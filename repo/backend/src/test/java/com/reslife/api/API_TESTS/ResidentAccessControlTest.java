package com.reslife.api.API_TESTS;

import com.reslife.api.domain.housing.BookingPolicyEnforcementService;
import com.reslife.api.domain.housing.ResidentBookingService;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.FilterOptionsResponse;
import com.reslife.api.domain.resident.Resident;
import com.reslife.api.domain.resident.ResidentController;
import com.reslife.api.domain.resident.ResidentService;
import com.reslife.api.domain.resident.StudentController;
import com.reslife.api.domain.user.AccountStatus;
import com.reslife.api.domain.user.Role;
import com.reslife.api.domain.user.RoleName;
import com.reslife.api.domain.user.User;
import com.reslife.api.domain.user.UserRepository;
import com.reslife.api.domain.user.UserRole;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the authorization boundaries introduced by Blocker #1:
 *
 * <ul>
 *   <li>Students are denied access to the resident directory and per-record detail.</li>
 *   <li>Students can fetch their own resident record via {@code GET /api/students/me}.</li>
 *   <li>Staff and admin retain access to the full resident directory and per-record detail.</li>
 * </ul>
 */
@WebMvcTest(controllers = {ResidentController.class, StudentController.class})
@Import(SecurityConfig.class)
class ResidentAccessControlTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Controller dependencies ───────────────────────────────────────────────
    @MockBean private ResidentService             residentService;
    @MockBean private BookingPolicyEnforcementService bookingPolicyEnforcementService;
    @MockBean private ResidentBookingService      residentBookingService;
    @MockBean private UserRepository              userRepository;

    // ── Security infrastructure needed by SecurityConfig / filters ────────────
    @MockBean private UserDetailsServiceImpl  userDetailsService;
    @MockBean private IntegrationAuthFilter   integrationAuthFilter;

    // ── Fixed test identifiers ────────────────────────────────────────────────
    private static final UUID   OTHER_RESIDENT_ID = UUID.randomUUID();
    private static final UUID   STUDENT_USER_ID   = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setup() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        when(residentService.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        Resident otherResident = new Resident();
        otherResident.setDateOfBirth(LocalDate.of(2000, 1, 1));
        when(residentService.findById(OTHER_RESIDENT_ID)).thenReturn(otherResident);
        when(residentService.filterOptions())
                .thenReturn(new FilterOptionsResponse(List.of(), List.of()));
        Resident linkedResident = new Resident();
        linkedResident.setFirstName("Student");
        linkedResident.setLastName("User");
        linkedResident.setEmail("student@reslife.local");
        linkedResident.setStudentId("S-100");
        linkedResident.setDateOfBirth(LocalDate.of(2005, 1, 1));
        when(residentService.findByLinkedUserId(STUDENT_USER_ID)).thenReturn(linkedResident);
    }

    // ── Student denied from resident directory ────────────────────────────────

    @Test
    void student_isDeniedFromResidentDirectory() throws Exception {
        mockMvc.perform(get("/api/residents")
                        .with(asUser(STUDENT_USER_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── Student denied from another resident's detail ─────────────────────────

    @Test
    void student_isDeniedFromOtherResidentDetail() throws Exception {
        mockMvc.perform(get("/api/residents/{id}", OTHER_RESIDENT_ID)
                        .with(asUser(STUDENT_USER_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── Student allowed to fetch their own resident record ────────────────────

    @Test
    void student_isAllowedToFetchSelfView() throws Exception {
        mockMvc.perform(get("/api/students/me")
                        .with(asUser(STUDENT_USER_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.firstName").exists())
               .andExpect(jsonPath("$.email").exists())
               .andExpect(jsonPath("$.dateOfBirth").value(nullValue()));
    }

    // ── Staff still allowed on the resident directory ─────────────────────────

    @Test
    void staff_isAllowedToListResidents() throws Exception {
        mockMvc.perform(get("/api/residents")
                        .with(asUser(UUID.randomUUID(), RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk());
    }

    // ── Admin still allowed on per-record detail ──────────────────────────────

    @Test
    void admin_isAllowedToGetResidentDetail() throws Exception {
        mockMvc.perform(get("/api/residents/{id}", OTHER_RESIDENT_ID)
                        .with(asUser(UUID.randomUUID(), RoleName.ADMIN)))
               .andExpect(status().isOk());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static RequestPostProcessor asUser(UUID userId, RoleName roleName) {
        ReslifeUserDetails details = buildDetails(userId, roleName);
        UsernamePasswordAuthenticationToken token =
                UsernamePasswordAuthenticationToken.authenticated(
                        details, null, details.getAuthorities());
        return authentication(token);
    }

    private static ReslifeUserDetails buildDetails(UUID userId, RoleName roleName) {
        Role role = mock(Role.class);
        when(role.getName()).thenReturn(roleName);

        UserRole userRole = mock(UserRole.class);
        when(userRole.getRole()).thenReturn(role);

        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getUsername()).thenReturn("testuser");
        when(user.getPasswordHash()).thenReturn("hash");
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(user.getUserRoles()).thenReturn(Set.of(userRole));

        return ReslifeUserDetails.from(user);
    }
}
