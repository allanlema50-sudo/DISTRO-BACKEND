package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequestDocument;
import com.example.distrobackend.Domain.enums.AccessRequestDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccessRequestDocumentRepository extends JpaRepository<AccessRequestDocument, UUID> {
    Optional<AccessRequestDocument> findByAccessRequestIdAndDocumentType(
            UUID accessRequestId, AccessRequestDocumentType documentType);
}
