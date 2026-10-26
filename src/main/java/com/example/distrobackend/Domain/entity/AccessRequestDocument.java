package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.AccessRequestDocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Private onboarding evidence. Its bytes are never included in API responses. */
@Entity
@Table(name = "access_request_documents",
        uniqueConstraints = @UniqueConstraint(name = "uq_access_request_document_type",
                columnNames = {"access_request_id", "document_type"}))
@Getter
@Setter
@NoArgsConstructor
public class AccessRequestDocument {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "access_request_id", nullable = false)
    private AccessRequest accessRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private AccessRequestDocumentType documentType;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "content", nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
