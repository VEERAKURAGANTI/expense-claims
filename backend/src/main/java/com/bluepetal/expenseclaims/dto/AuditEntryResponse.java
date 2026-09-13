package com.bluepetal.expenseclaims.dto;

import com.bluepetal.expenseclaims.model.ClaimAudit;

import java.time.LocalDateTime;

public record AuditEntryResponse(String actorName, String action, String note, LocalDateTime createdAt) {
    public static AuditEntryResponse from(ClaimAudit a) {
        return new AuditEntryResponse(
                a.getActor() == null ? "System" : a.getActor().getName(),
                a.getAction(),
                a.getNote(),
                a.getCreatedAt()
        );
    }
}
