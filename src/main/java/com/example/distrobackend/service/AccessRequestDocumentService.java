package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.AccessRequestDocument;
import com.example.distrobackend.Domain.enums.AccessRequestDocumentType;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.AccessRequestDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Validates and persists onboarding evidence in private database storage.
 * The generated document id is an internal reference only; no public URL is exposed.
 */
@Service
@RequiredArgsConstructor
public class AccessRequestDocumentService {
    static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final AccessRequestDocumentRepository documentRepository;

    public void storeRequiredDocuments(AccessRequest request,
                                       MultipartFile incorporationCertificate,
                                       MultipartFile taxCertificate) {
        store(request, AccessRequestDocumentType.INCORPORATION_CERTIFICATE,
                incorporationCertificate);
        store(request, AccessRequestDocumentType.TAX_CERTIFICATE, taxCertificate);
    }

    public AccessRequestDocument findForAdmin(UUID accessRequestId,
                                              AccessRequestDocumentType documentType) {
        return documentRepository.findByAccessRequestIdAndDocumentType(accessRequestId, documentType)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_REQUEST_NOT_FOUND,
                        "The requested access-request document was not found"));
    }

    private void store(AccessRequest request,
                       AccessRequestDocumentType documentType,
                       MultipartFile file) {
        byte[] content = readAndValidate(file, documentType.name());
        String contentType = detectContentType(content);

        AccessRequestDocument document = new AccessRequestDocument();
        document.setAccessRequest(request);
        document.setDocumentType(documentType);
        document.setContentType(contentType);
        document.setFileSize(content.length);
        document.setContent(content);
        documentRepository.save(document);
    }

    private byte[] readAndValidate(MultipartFile file, String fieldName) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, fieldName + " is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    fieldName + " must not exceed 5 MB");
        }

        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = readAtMost(input, MAX_FILE_SIZE_BYTES);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Unable to read " + fieldName);
        }

        String detected = detectContentType(content);
        String declared = canonicalizeDeclaredType(file.getContentType());
        if (detected == null || (declared != null && !declared.equals(detected))) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    fieldName + " must be a valid PDF, JPG, or PNG file");
        }
        return content;
    }

    private static byte[] readAtMost(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Uploaded file must not exceed 5 MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String canonicalizeDeclaredType(String declared) {
        if (declared == null || declared.isBlank() || "application/octet-stream".equalsIgnoreCase(declared)) {
            return null;
        }
        if ("application/pdf".equalsIgnoreCase(declared)) {
            return "application/pdf";
        }
        if ("image/png".equalsIgnoreCase(declared)) {
            return "image/png";
        }
        if ("image/jpeg".equalsIgnoreCase(declared) || "image/jpg".equalsIgnoreCase(declared)) {
            return "image/jpeg";
        }
        return "unsupported";
    }

    private static String detectContentType(byte[] content) {
        if (content.length >= 5
                && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
                && content[3] == 'F' && content[4] == '-') {
            return "application/pdf";
        }
        if (content.length >= 8
                && (content[0] & 0xFF) == 0x89 && content[1] == 0x50
                && content[2] == 0x4E && content[3] == 0x47
                && content[4] == 0x0D && content[5] == 0x0A
                && content[6] == 0x1A && content[7] == 0x0A) {
            return "image/png";
        }
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xFF
                && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF
                && content.length >= 4
                && (content[content.length - 2] & 0xFF) == 0xFF
                && (content[content.length - 1] & 0xFF) == 0xD9) {
            return "image/jpeg";
        }
        return null;
    }
}
