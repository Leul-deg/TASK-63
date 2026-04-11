package com.reslife.api.domain.resident;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResidentImportExportServiceTest {

    private final ResidentRepository residentRepository = mock(ResidentRepository.class);
    private final ResidentService residentService = mock(ResidentService.class);

    private ResidentImportExportService service;

    @BeforeEach
    void setUp() {
        service = new ResidentImportExportService(residentRepository, residentService);
    }

    @Test
    void preview_marksNameAndDobMatchAsMergeCandidate() throws Exception {
        Resident existing = mock(Resident.class);
        when(existing.getId()).thenReturn(UUID.randomUUID());
        when(existing.getFirstName()).thenReturn("Alex");
        when(existing.getLastName()).thenReturn("Chen");
        when(existing.getEmail()).thenReturn("alex.existing@campus.edu");
        when(existing.getStudentId()).thenReturn("S-100");
        when(existing.getDateOfBirth()).thenReturn(LocalDate.of(2005, 1, 1));

        when(residentRepository.findByStudentId("S-200")).thenReturn(Optional.empty());
        when(residentRepository.findByFirstNameIgnoreCaseAndLastNameIgnoreCase("Alex", "Chen"))
                .thenReturn(List.of(existing));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "residents.csv",
                "text/csv",
                """
                studentId,firstName,lastName,email,phone,dateOfBirth,enrollmentStatus,department,classYear,roomNumber,buildingName
                S-200,Alex,Chen,alex.new@campus.edu,555-123-4567,2005-01-01,ENROLLED,CS,2027,101,Maple Hall
                """.getBytes()
        );

        ImportPreviewResponse preview = service.preview(file);

        assertEquals(1, preview.rows().size());
        ImportRowPreview row = preview.rows().getFirst();
        assertEquals(ImportRowPreview.RowStatus.MERGE_CANDIDATE, row.status());
        assertNotNull(row.match());
        assertEquals("name+dob", row.match().matchReason());
    }

    @Test
    void preview_marksRepeatedStudentIdInSameFileAsMergeCandidate() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "residents.csv",
                "text/csv",
                """
                studentId,firstName,lastName,email,phone,dateOfBirth,enrollmentStatus,department,classYear,roomNumber,buildingName
                S-200,Alex,Chen,alex.one@campus.edu,555-123-4567,2005-01-01,ENROLLED,CS,2027,101,Maple Hall
                S-200,Alex,Chen,alex.two@campus.edu,555-123-4567,2005-01-01,ENROLLED,CS,2027,101,Maple Hall
                """.getBytes()
        );

        ImportPreviewResponse preview = service.preview(file);

        assertEquals(2, preview.rows().size());
        assertEquals(ImportRowPreview.RowStatus.NEW, preview.rows().get(0).status());
        ImportRowPreview secondRow = preview.rows().get(1);
        assertEquals(ImportRowPreview.RowStatus.MERGE_CANDIDATE, secondRow.status());
        assertNotNull(secondRow.match());
        assertEquals("studentId", secondRow.match().matchReason());
        assertEquals(1, secondRow.match().sourceRowNumber());
    }

    @Test
    void commit_canMergeIntoEarlierRowFromSameFile() {
        Resident created = mock(Resident.class);
        UUID createdId = UUID.randomUUID();
        when(created.getId()).thenReturn(createdId);
        when(residentService.create(any(ResidentRequest.class), eq(true))).thenReturn(created);

        ImportRowData first = new ImportRowData(
                "S-200", "Alex", "Chen", "alex.one@campus.edu", "555-123-4567",
                "2005-01-01", "ENROLLED", "CS", "2027", "101", "Maple Hall");
        ImportRowData second = new ImportRowData(
                "S-200", "Alex", "Chen", "alex.two@campus.edu", "555-123-4567",
                "2005-01-01", "ENROLLED", "CS", "2027", "101", "Maple Hall");

        ImportCommitResponse result = service.commit(new ImportCommitRequest(List.of(
                new ImportCommitRequest.CommitDecision(1, ImportCommitRequest.RowAction.CREATE, null, null, first),
                new ImportCommitRequest.CommitDecision(2, ImportCommitRequest.RowAction.MERGE, null, 1, second)
        )));

        assertEquals(1, result.created());
        assertEquals(1, result.merged());
        assertEquals(0, result.failed());
        verify(residentService).update(eq(createdId), any(ResidentRequest.class));
    }
}
