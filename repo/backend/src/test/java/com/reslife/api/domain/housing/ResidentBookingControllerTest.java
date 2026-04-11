package com.reslife.api.domain.housing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.ResidentController;
import com.reslife.api.domain.resident.ResidentService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ResidentController.class)
@Import(SecurityConfig.class)
class ResidentBookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ResidentService residentService;
    @MockBean private BookingPolicyEnforcementService bookingPolicyEnforcementService;
    @MockBean private ResidentBookingService residentBookingService;
    @MockBean private UserDetailsServiceImpl userDetailsService;
    @MockBean private IntegrationAuthFilter integrationAuthFilter;
    @MockBean private UserRepository userRepository;

    private static final UUID RESIDENT_ID = UUID.randomUUID();
    private static final UUID BOOKING_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        ResidentBooking booking = mock(ResidentBooking.class);
        com.reslife.api.domain.resident.Resident resident = mock(com.reslife.api.domain.resident.Resident.class);
        when(resident.getId()).thenReturn(RESIDENT_ID);
        when(booking.getId()).thenReturn(BOOKING_ID);
        when(booking.getResident()).thenReturn(resident);
        when(booking.getRequestedDate()).thenReturn(LocalDate.now().plusDays(2));
        when(booking.getBuildingName()).thenReturn("Maple Hall");
        when(booking.getRoomNumber()).thenReturn("101");
        when(booking.getStatus()).thenReturn(ResidentBookingStatus.REQUESTED);
        when(booking.getPurpose()).thenReturn("Family visit");

        when(residentBookingService.createBooking(eq(RESIDENT_ID), any(), any())).thenReturn(booking);
    }

    @Test
    void staff_canCreateBooking() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/bookings", RESIDENT_ID)
                        .with(asUser(RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ResidentBookingRequest(
                                LocalDate.now().plusDays(2), "Maple Hall", "101", "Family visit", null
                        ))))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
               .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void student_isDeniedFromCreatingBooking() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/bookings", RESIDENT_ID)
                        .with(asUser(RoleName.STUDENT))
                        .with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ResidentBookingRequest(
                                LocalDate.now().plusDays(2), "Maple Hall", "101", "Family visit", null
                        ))))
               .andExpect(status().isForbidden());
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
