package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequestDocument;
import com.example.distrobackend.Domain.enums.AccessRequestDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AccessRequestDocumentRepository extends JpaRepository<AccessRequestDocument, UUID> {
    Optional<AccessRequestDocument> findByAccessRequestIdAndDocumentType(
            UUID accessRequestId, AccessRequestDocumentType documentType);

    @Query("select coalesce(sum(document.fileSize), 0) from AccessRequestDocument document")
    long sumFileSize();

    @Modifying
    @Query(value = """
            DELETE FROM access_request_documents document
            USING access_requests request
            WHERE document.access_request_id = request.id
              AND request.status IN ('REJECTED', 'EXPIRED')
              AND request.updated_at < :cutoff
            """, nativeQuery = true)
    int deleteRejectedOrExpiredDocumentsBefore(@Param("cutoff") java.time.OffsetDateTime cutoff);
}
