
package com.example.distrobackend.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record AdminAuditLogResponse(
    Long id,
    String userId,
    String userName,
    String userEmail,
    String action,
    String resourceType,
    String resourceId,
    String description,
    String ipAddress,
    String status,
    OffsetDateTime createdAt,
    Map<String, Object> metadata
) {
}
