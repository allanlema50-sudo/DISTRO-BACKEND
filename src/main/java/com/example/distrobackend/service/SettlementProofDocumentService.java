package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.SettlementProofDocument;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.SettlementProofDocumentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * Bank-slip evidence. Same rules as onboarding documents: PDF, JPG or PNG only,
 * the content must match its magic bytes and any declared type, and the size is
 * capped. Bytes live in the database and are served only through an
 * authenticated download for the supplier organization or a super admin.
 */
@Service
@RequiredArgsConstructor
public class SettlementProofDocumentService {

    static final int MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    private final SettlementProofDocumentRepository documentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional
    public SettlementProofDocument store(PurchaseOrder order, User uploadedBy, MultipartFile file) {
        ValidatedDocument validated = validate(file);
        SettlementProofDocument document = new SettlementProofDocument();
        document.setPurchaseOrder(order);
        document.setUploadedBy(uploadedBy);
        document.setContentType(validated.contentType());
        document.setFileSize(validated.content().length);
        document.setContent(validated.content());
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public ProofDownload download(AuthenticatedUser actor, UUID purchaseOrderId, UUID documentId) {
        PurchaseOrder order = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        boolean superAdmin = actor != null && actor.role() == UserRole.SUPER_ADMIN;
        boolean supplier = actor != null && actor.organizationId() != null && actor.role() != null
                && actor.role().organizationtype() == Organizationtype.MANUFACTURER
                && actor.organizationId().equals(order.getSupplierOrganization().getId());
        if (!superAdmin && !supplier) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        SettlementProofDocument document = documentRepository
                .findByIdAndPurchaseOrder_Id(documentId, purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment proof not found"));
        String extension = switch (document.getContentType()) {
            case "application/pdf" -> ".pdf";
            case "image/png" -> ".png";
            default -> ".jpg";
        };
        return new ProofDownload(document.getContent(), document.getContentType(),
                "payment-proof-" + document.getId() + extension);
    }

    public record ProofDownload(byte[] content, String contentType, String filename) { }

    private static ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A payment proof file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "The payment proof must not exceed 5 MB");
        }
        byte[] content;
        try (InputStream input = file.getInputStream()) {
            content = readAtMost(input, MAX_FILE_SIZE_BYTES);
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unable to read the payment proof");
        }
        String detected = detectContentType(content);
        String declared = canonicalizeDeclaredType(file.getContentType());
        if (detected == null || (declared != null && !declared.equals(detected))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "The payment proof must be a valid PDF, JPG, or PNG file");
        }
        return new ValidatedDocument(content, detected);
    }

    private static byte[] readAtMost(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "The payment proof must not exceed 5 MB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String canonicalizeDeclaredType(String declared) {
        if (declared == null || declared.isBlank() || "application/octet-stream".equalsIgnoreCase(declared)) {
            return null;
        }
        if ("application/pdf".equalsIgnoreCase(declared)) return "application/pdf";
        if ("image/png".equalsIgnoreCase(declared)) return "image/png";
        if ("image/jpeg".equalsIgnoreCase(declared) || "image/jpg".equalsIgnoreCase(declared)) return "image/jpeg";
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
                && (content[2] & 0xFF) == 0xFF) {
            for (int index = 3; index < content.length - 1; index++) {
                if ((content[index] & 0xFF) == 0xFF && (content[index + 1] & 0xFF) == 0xD9) {
                    return "image/jpeg";
                }
            }
        }
        return null;
    }

    private record ValidatedDocument(byte[] content, String contentType) { }
}
