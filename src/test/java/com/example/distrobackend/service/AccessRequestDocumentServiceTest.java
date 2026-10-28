package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.AccessRequestDocument;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.AccessRequestDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRequestDocumentServiceTest {

    @Mock
    private AccessRequestDocumentRepository documentRepository;

    private AccessRequestDocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new AccessRequestDocumentService(documentRepository);
        ReflectionTestUtils.setField(documentService, "maxDocumentStorage", DataSize.ofMegabytes(10));
        org.mockito.Mockito.lenient().when(documentRepository.sumFileSize()).thenReturn(0L);
    }

    @Test
    void storesBothDocumentsAfterValidatingTheirSignatures() {
        AccessRequest request = new AccessRequest();

        documentService.storeRequiredDocuments(request,
                new MockMultipartFile("incorporationCertificate", "incorporation.pdf",
                        "application/pdf", "%PDF-1.7\n".getBytes()),
                new MockMultipartFile("taxCertificate", "tax.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

        ArgumentCaptor<AccessRequestDocument> documents = ArgumentCaptor.forClass(AccessRequestDocument.class);
        verify(documentRepository, times(2)).save(documents.capture());
        assertThat(documents.getAllValues()).allSatisfy(document -> {
            assertThat(document.getAccessRequest()).isSameAs(request);
            assertThat(document.getFileSize()).isPositive();
            assertThat(document.getContent()).isNotEmpty();
        });
    }

    @Test
    void rejectsAFileWithAValidExtensionButInvalidContent() {
        AccessRequest request = new AccessRequest();
        MockMultipartFile invalid = new MockMultipartFile(
                "incorporationCertificate", "incorporation.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> documentService.storeRequiredDocuments(request, invalid,
                new MockMultipartFile("taxCertificate", "tax.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("INCORPORATION_CERTIFICATE");
    }

    @Test
    void rejectsAnOversizedDocumentBeforePersistingIt() {
        byte[] oversized = new byte[AccessRequestDocumentService.MAX_FILE_SIZE_BYTES + 1];
        oversized[0] = '%';
        MockMultipartFile file = new MockMultipartFile(
                "incorporationCertificate", "incorporation.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> documentService.storeRequiredDocuments(new AccessRequest(), file,
                new MockMultipartFile("taxCertificate", "tax.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("5 MB");

        verify(documentRepository, times(0)).save(any(AccessRequestDocument.class));
    }

    @Test
    void acceptsJpegWithTrailingDataAfterTheEndOfImageMarker() {
        byte[] jpegWithTrailingData = new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00,
                (byte) 0xFF, (byte) 0xD9, 0x00, 0x01
        };

        documentService.storeRequiredDocuments(new AccessRequest(),
                new MockMultipartFile("incorporationCertificate", "incorporation.jpg",
                        "image/jpeg", jpegWithTrailingData),
                new MockMultipartFile("taxCertificate", "tax.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}));

        verify(documentRepository, times(2)).save(any(AccessRequestDocument.class));
    }

    @Test
    void rejectsDocumentsWhenThePrivateStorageQuotaWouldBeExceeded() {
        ReflectionTestUtils.setField(documentService, "maxDocumentStorage", DataSize.ofBytes(10));
        when(documentRepository.sumFileSize()).thenReturn(0L);

        assertThatThrownBy(() -> documentService.storeRequiredDocuments(new AccessRequest(),
                new MockMultipartFile("incorporationCertificate", "incorporation.pdf",
                        "application/pdf", "%PDF-1.7\n".getBytes()),
                new MockMultipartFile("taxCertificate", "tax.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("temporarily unavailable");

        verify(documentRepository, times(0)).save(any(AccessRequestDocument.class));
    }
}
