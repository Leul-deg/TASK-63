package com.reslife.api.API_TESTS;

import com.reslife.api.config.SecurityConfig;
import com.reslife.api.domain.housing.*;
import com.reslife.api.domain.integration.IntegrationAuthFilter;
import com.reslife.api.domain.resident.*;
import com.reslife.api.domain.user.*;
import com.reslife.api.security.ReslifeUserDetails;
import com.reslife.api.security.UserDetailsServiceImpl;
import jakarta.persistence.EntityNotFoundException;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * HTTP-layer tests for {@link ResidentController} covering the endpoints that
 * were not reached by the earlier access-control and booking tests.
 *
 * <ul>
 *   <li>filter-options, duplicate-check</li>
 *   <li>create (201), update (200), soft-delete (204)</li>
 *   <li>emergency contacts CRUD</li>
 *   <li>move-in records CRUD</li>
 *   <li>bookings list and status update</li>
 *   <li>student denied from staff-only endpoints</li>
 * </ul>
 */
@WebMvcTest(controllers = ResidentController.class)
@Import(SecurityConfig.class)
class ResidentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean private ResidentService                residentService;
    @MockBean private BookingPolicyEnforcementService bookingPolicyEnforcementService;
    @MockBean private ResidentBookingService          residentBookingService;
    @MockBean private UserDetailsServiceImpl          userDetailsService;
    @MockBean private IntegrationAuthFilter           integrationAuthFilter;
    @MockBean private UserRepository                  userRepository;

    private static final UUID RESIDENT_ID  = UUID.randomUUID();
    private static final UUID STAFF_ID     = UUID.randomUUID();
    private static final UUID ADMIN_ID     = UUID.randomUUID();
    private static final UUID STUDENT_ID   = UUID.randomUUID();
    private static final UUID CONTACT_ID   = UUID.randomUUID();
    private static final UUID RECORD_ID    = UUID.randomUUID();
    private static final UUID BOOKING_ID   = UUID.randomUUID();

    private ResidentResponse          stubResident;
    private EmergencyContactResponse  stubContact;
    private MoveInRecordResponse      stubRecord;
    private ResidentBookingResponse   stubBooking;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            FilterChain chain = inv.getArgument(2);
            chain.doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(integrationAuthFilter).doFilter(any(), any(), any());

        User active = new User();
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(active));

        stubResident = new ResidentResponse(
                RESIDENT_ID, "S001", "Alice", "Smith", "alice@test.com",
                "555-123-4567", null, "ENROLLED", "CS", "101A", "North Hall", 2026);
        stubContact = new EmergencyContactResponse(
                CONTACT_ID, "Bob Smith", "Parent", "555-111-2222", "bob@test.com", true);
        stubRecord = new MoveInRecordResponse(
                RECORD_ID, "101A", "North Hall", LocalDate.now(), null, CheckInStatus.PENDING, null);
        stubBooking = new ResidentBookingResponse(
                BOOKING_ID, RESIDENT_ID, LocalDate.now().plusDays(3),
                "North Hall", "101A", ResidentBookingStatus.REQUESTED,
                "Visit", null, null, STAFF_ID);

        when(residentService.search(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(residentService.filterOptions())
                .thenReturn(new FilterOptionsResponse(List.of("North Hall"), List.of(2026)));
        when(residentService.checkDuplicates(any(), any(), any(), any(), any(), any()))
                .thenReturn(new DuplicateCheckResponse(List.of()));
        // Pre-create stubs to avoid nested when() inside thenReturn()
        Resident residentForFindById = stubResident(RESIDENT_ID);
        Resident residentForCreate   = stubResident(RESIDENT_ID);
        Resident residentForUpdate   = stubResident(RESIDENT_ID);
        EmergencyContact ecForAdd    = stubEc();
        EmergencyContact ecForUpdate = stubEc();
        MoveInRecord mirForAdd       = stubMir();
        MoveInRecord mirForUpdate    = stubMir();
        ResidentBooking rbrForStatus = stubRbr();

        when(residentService.findById(eq(RESIDENT_ID))).thenReturn(residentForFindById);
        when(residentService.create(any(), anyBoolean())).thenReturn(residentForCreate);
        when(residentService.update(eq(RESIDENT_ID), any())).thenReturn(residentForUpdate);
        doNothing().when(residentService).softDelete(eq(RESIDENT_ID));
        when(residentService.findEmergencyContacts(eq(RESIDENT_ID))).thenReturn(List.of());
        when(residentService.addEmergencyContact(eq(RESIDENT_ID), any(EmergencyContactRequest.class))).thenReturn(ecForAdd);
        when(residentService.updateEmergencyContact(eq(RESIDENT_ID), eq(CONTACT_ID), any())).thenReturn(ecForUpdate);
        doNothing().when(residentService).removeEmergencyContact(eq(RESIDENT_ID), eq(CONTACT_ID));
        when(residentService.findMoveInRecords(eq(RESIDENT_ID))).thenReturn(List.of());
        when(residentService.addMoveInRecord(eq(RESIDENT_ID), any())).thenReturn(mirForAdd);
        when(residentService.updateMoveInRecord(eq(RESIDENT_ID), eq(RECORD_ID), any())).thenReturn(mirForUpdate);
        when(residentBookingService.findByResident(eq(RESIDENT_ID))).thenReturn(List.of());
        when(residentBookingService.updateStatus(eq(RESIDENT_ID), eq(BOOKING_ID), any(), any(), any()))
                .thenReturn(rbrForStatus);
    }

    // ── GET /api/residents/filter-options ─────────────────────────────────────

    @Test
    void staff_canGetFilterOptions() throws Exception {
        mockMvc.perform(get("/api/residents/filter-options")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.buildings[0]").value("North Hall"));
    }

    @Test
    void student_cannotGetFilterOptions() throws Exception {
        mockMvc.perform(get("/api/residents/filter-options")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT)))
               .andExpect(status().isForbidden());
    }

    // ── GET /api/residents/duplicate-check ────────────────────────────────────

    @Test
    void staff_canRunDuplicateCheck() throws Exception {
        mockMvc.perform(get("/api/residents/duplicate-check")
                        .param("firstName", "Alice")
                        .param("lastName", "Smith")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.candidates").isArray());
    }

    // ── POST /api/residents ───────────────────────────────────────────────────

    @Test
    void staff_canCreateResident() throws Exception {
        mockMvc.perform(post("/api/residents")
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Alice","lastName":"Smith",
                                 "email":"alice@test.com"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(RESIDENT_ID.toString()));
    }

    @Test
    void student_cannotCreateResident() throws Exception {
        mockMvc.perform(post("/api/residents")
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Alice","lastName":"Smith","email":"a@t.com"}
                                """))
               .andExpect(status().isForbidden());
    }

    // ── PUT /api/residents/{id} ───────────────────────────────────────────────

    @Test
    void staff_canUpdateResident() throws Exception {
        mockMvc.perform(put("/api/residents/{id}", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Alice","lastName":"Jones","email":"alice@test.com"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(RESIDENT_ID.toString()));
    }

    // ── DELETE /api/residents/{id} ────────────────────────────────────────────

    @Test
    void admin_canDeleteResident() throws Exception {
        mockMvc.perform(delete("/api/residents/{id}", RESIDENT_ID)
                        .with(asUser(ADMIN_ID, RoleName.ADMIN))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    @Test
    void staff_cannotDeleteResident() throws Exception {
        mockMvc.perform(delete("/api/residents/{id}", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isForbidden());
    }

    // ── GET /api/residents/{id}/emergency-contacts ────────────────────────────

    @Test
    void staff_canListEmergencyContacts() throws Exception {
        mockMvc.perform(get("/api/residents/{id}/emergency-contacts", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    // ── POST /api/residents/{id}/emergency-contacts ───────────────────────────

    @Test
    void staff_canAddEmergencyContact() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/emergency-contacts", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bob Smith","relationship":"Parent",
                                 "phone":"555-111-2222","primary":true}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(CONTACT_ID.toString()));
    }

    // ── PUT /api/residents/{id}/emergency-contacts/{contactId} ───────────────

    @Test
    void staff_canUpdateEmergencyContact() throws Exception {
        mockMvc.perform(put("/api/residents/{id}/emergency-contacts/{cid}", RESIDENT_ID, CONTACT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bob Jones","relationship":"Parent",
                                 "phone":"555-111-3333","primary":true}
                                """))
               .andExpect(status().isOk());
    }

    // ── DELETE /api/residents/{id}/emergency-contacts/{contactId} ────────────

    @Test
    void staff_canRemoveEmergencyContact() throws Exception {
        mockMvc.perform(delete("/api/residents/{id}/emergency-contacts/{cid}", RESIDENT_ID, CONTACT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf()))
               .andExpect(status().isNoContent());
    }

    // ── GET /api/residents/{id}/move-in-records ───────────────────────────────

    @Test
    void staff_canListMoveInRecords() throws Exception {
        mockMvc.perform(get("/api/residents/{id}/move-in-records", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    // ── POST /api/residents/{id}/move-in-records ──────────────────────────────

    @Test
    void staff_canAddMoveInRecord() throws Exception {
        mockMvc.perform(post("/api/residents/{id}/move-in-records", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101A","buildingName":"North Hall",
                                 "moveInDate":"2026-09-01"}
                                """))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.id").value(RECORD_ID.toString()));
    }

    // ── PUT /api/residents/{id}/move-in-records/{recordId} ───────────────────

    @Test
    void staff_canUpdateMoveInRecord() throws Exception {
        mockMvc.perform(put("/api/residents/{id}/move-in-records/{rid}", RESIDENT_ID, RECORD_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomNumber":"101A","buildingName":"North Hall",
                                 "moveInDate":"2026-09-01","checkInStatus":"CHECKED_IN"}
                                """))
               .andExpect(status().isOk());
    }

    // ── GET /api/residents/{id}/bookings ──────────────────────────────────────

    @Test
    void staff_canListBookings() throws Exception {
        mockMvc.perform(get("/api/residents/{id}/bookings", RESIDENT_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray());
    }

    // ── PATCH /api/residents/{id}/bookings/{bookingId}/status ─────────────────

    @Test
    void staff_canUpdateBookingStatus() throws Exception {
        mockMvc.perform(patch("/api/residents/{id}/bookings/{bid}/status", RESIDENT_ID, BOOKING_ID)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED","reason":"Approved by staff"}
                                """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()));
    }

    @Test
    void student_cannotUpdateBookingStatus() throws Exception {
        mockMvc.perform(patch("/api/residents/{id}/bookings/{bid}/status", RESIDENT_ID, BOOKING_ID)
                        .with(asUser(STUDENT_ID, RoleName.STUDENT))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}
                                """))
               .andExpect(status().isForbidden());
    }

    // ── Entity not found ──────────────────────────────────────────────────────

    @Test
    void getResident_notFound_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        when(residentService.findById(eq(missing)))
                .thenThrow(new EntityNotFoundException("Resident not found"));

        mockMvc.perform(get("/api/residents/{id}", missing)
                        .with(asUser(STAFF_ID, RoleName.RESIDENCE_STAFF)))
               .andExpect(status().isNotFound());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Minimal Resident entity stub — only getId() is needed by the controller. */
    private Resident stubResident(UUID id) {
        Resident r = mock(Resident.class);
        when(r.getId()).thenReturn(id);
        when(r.getFirstName()).thenReturn("Alice");
        when(r.getLastName()).thenReturn("Smith");
        when(r.getEmail()).thenReturn("alice@test.com");
        when(r.getStudentId()).thenReturn("S001");
        when(r.getClassYear()).thenReturn(2026);
        return r;
    }

    private com.reslife.api.domain.resident.EmergencyContact stubEc() {
        var ec = mock(com.reslife.api.domain.resident.EmergencyContact.class);
        when(ec.getId()).thenReturn(CONTACT_ID);
        when(ec.getName()).thenReturn("Bob Smith");
        when(ec.getRelationship()).thenReturn("Parent");
        when(ec.getPhone()).thenReturn("555-111-2222");
        when(ec.isPrimary()).thenReturn(true);
        return ec;
    }

    private MoveInRecord stubMir() {
        MoveInRecord r = mock(MoveInRecord.class);
        when(r.getId()).thenReturn(RECORD_ID);
        when(r.getRoomNumber()).thenReturn("101A");
        when(r.getBuildingName()).thenReturn("North Hall");
        when(r.getMoveInDate()).thenReturn(LocalDate.now());
        when(r.getCheckInStatus()).thenReturn(CheckInStatus.PENDING);
        return r;
    }

    private ResidentBooking stubRbr() {
        ResidentBooking b = mock(ResidentBooking.class);
        Resident res = mock(Resident.class);
        when(res.getId()).thenReturn(RESIDENT_ID);
        when(b.getId()).thenReturn(BOOKING_ID);
        when(b.getResident()).thenReturn(res);
        when(b.getRequestedDate()).thenReturn(LocalDate.now().plusDays(3));
        when(b.getStatus()).thenReturn(ResidentBookingStatus.CONFIRMED);
        return b;
    }

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
