package com.bluepetal.expenseclaims.dto;

import com.bluepetal.expenseclaims.model.Claim;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClaimResponse(
        Long id,
        Long ownerId,
        String ownerName,
        String rawText,
        String merchant,
        BigDecimal amount,
        String category,
        LocalDate expenseDate,
        String description,
        boolean hasAttachment,
        String status,
        Long possibleDuplicateOf,
        boolean duplicateAcknowledged,
        String decidedByName,
        LocalDateTime decidedAt,
        String managerNote,
        String paidByName,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ClaimResponse from(Claim c) {
        return new ClaimResponse(
                c.getId(),
                c.getOwner().getId(),
                c.getOwner().getName(),
                c.getRawText(),
                c.getMerchant(),
                c.getAmount(),
                c.getCategory() == null ? null : c.getCategory().name(),
                c.getExpenseDate(),
                c.getDescription(),
                c.getReceiptPath() != null,
                c.getStatus().name(),
                c.getPossibleDuplicateOf(),
                c.isDuplicateAcknowledged(),
                c.getDecidedBy() == null ? null : c.getDecidedBy().getName(),
                c.getDecidedAt(),
                c.getManagerNote(),
                c.getPaidBy() == null ? null : c.getPaidBy().getName(),
                c.getPaidAt(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }
}
