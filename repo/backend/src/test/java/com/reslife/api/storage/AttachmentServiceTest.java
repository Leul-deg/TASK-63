package com.reslife.api.storage;

import com.reslife.api.domain.housing.AgreementAttachment;
import com.reslife.api.domain.housing.AgreementAttachmentRepository;
import com.reslife.api.domain.housing.HousingAgreement;
import com.reslife.api.domain.housing.HousingAgreementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttachmentServiceTest {

    private final HousingAgreementRepository agreementRepository = mock(HousingAgreementRepository.class);
    private final AgreementAttachmentRepository attachmentRepository = mock(AgreementAttachmentRepository.class);
    private final StorageService storageService = mock(StorageService.class);

    private AttachmentService attachmentService;
    private UUID agreementId;

    @BeforeEach
    void setUp() {
        attachmentService = new AttachmentService(
                agreementRepository, attachmentRepository, storageService);
        agreementId = UUID.randomUUID();

        HousingAgreement agreement = mock(HousingAgreement.class);
        when(agreement.getId()).thenReturn(agreementId);
        when(agreementRepository.findById(agreementId)).thenReturn(Optional.of(agreement));
        when(attachmentRepository.save(any(AgreementAttachment.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void upload_acceptsValidPdf() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "agreement.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}
        );

        AgreementAttachment saved = attachmentService.upload(agreementId, file, "staff");

        assertEquals("agreement.pdf", saved.getOriginalFilename());
        verify(storageService).store(eq(agreementId), any(), eq(file));
    }

    @Test
    void upload_rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "big.pdf",
                "application/pdf",
                new byte[(int) AttachmentService.MAX_SIZE_BYTES + 1]
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attachmentService.upload(agreementId, file, "staff"));

        assertTrue(ex.getMessage().contains("15 MB"));
    }

    @Test
    void upload_rejectsMagicByteMismatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "fake.pdf",
                "application/pdf",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attachmentService.upload(agreementId, file, "staff"));

        assertTrue(ex.getMessage().contains("declared type"));
    }

    @Test
    void upload_rejectsMimeAndExtensionMismatch() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/jpeg",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> attachmentService.upload(agreementId, file, "staff"));

        assertTrue(ex.getMessage().contains("content type"));
    }
}
