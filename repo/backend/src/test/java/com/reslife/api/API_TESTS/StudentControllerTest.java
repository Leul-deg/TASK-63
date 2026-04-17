package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.housing.ResidentBooking;
import com.reslife.api.domain.housing.ResidentBookingService;
import com.reslife.api.domain.housing.ResidentBookingStatus;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.*;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link StudentController}.
 *
 * <ul>
 *   <li>Both endpoints require the {@code STUDENT} role.</li>
 *   <li>Staff/admin users receive 403.</li>
 *   <li>Unauthenticated requests receive 401.</li>
 * </ul>
 */
@WebMvcTest(controllers = StudentController.class)
@Import(SecurityConfig.class)
class StudentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private ResidentService        residentService;
    @MockBean private ResidentBookingService residentBookingService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter  integrationAuthFilter;
    @MockBean private UserRepository         userRepository;

    private static final UUID STUDENT_ID   = UUID.randomUUID();
    private static final UUID STAFF_ID     = UUID.randomUUID();
    private static final UUID RESIDENT_ID  = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(new User()));

        Resident stubResident = mock(Resident.class);
        when(stubResident.getId()).thenReturn(RESIDENT_ID);
        when(stubResident.getFirstName()).thenReturn("Alice");
        when(stubResident.getLastName()).thenReturn("Smith");
        when(stubResident.getStudentId()).thenReturn("S123");
        when(stubResident.getBuildingName()).thenReturn("Main Hall");
        when(stubResident.getRoomNumber()).thenReturn("101");
        when(residentService.findByLinkedUserId(any(UUID.class))).thenReturn(stubResident);

        ResidentBooking stubBooking = mock(ResidentBooking.class);
        when(stubBooking.getId()).thenReturn(UUID.randomUUID());
        when(stubBooking.getResident()).thenReturn(stubResident);
        when(stubBooking.getRequestedDate()).thenReturn(LocalDate.now());
        when(stubBooking.getBuildingName()).thenReturn("Main Hall");
        when(stubBooking.getRoomNumber()).thenReturn("101");
        when(stubBooking.getStatus()).thenReturn(ResidentBookingStatus.CONFIRMED);
        when(stubBooking.getPurpose()).thenReturn("Move-in");
        when(stubBooking.getNotes()).thenReturn(null);
        when(stubBooking.getDecisionReason()).thenReturn(null);
        when(stubBooking.getCreatedBy()).thenReturn(null);
        when(residentBookingService.findByResident(RESIDENT_ID)).thenReturn(List.of(stubBooking));
    }

    // ── GET /api/students/me ──────────────────────────────────────────────────

    @Test
    void student_canGetSelf() throws Exception {
        mockMvc.perform(get("/api/students/me")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isOk());
    }

    @Test
    void staff_cannotGetStudentMe() throws Exception {
        mockMvc.perform(get("/api/students/me")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotGetStudentMe() throws Exception {
        mockMvc.perform(get("/api/students/me"))
               .andExpect(status().isUnauthorized());
    }

    // ── GET /api/students/me/bookings ─────────────────────────────────────────

    @Test
    void student_canGetMyBookings() throws Exception {
        mockMvc.perform(get("/api/students/me/bookings")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    @Test
    void staff_cannotGetStudentBookings() throws Exception {
        mockMvc.perform(get("/api/students/me/bookings")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_cannotGetStudentBookings() throws Exception {
        mockMvc.perform(get("/api/students/me/bookings"))
               .andExpect(status().isUnauthorized());
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
