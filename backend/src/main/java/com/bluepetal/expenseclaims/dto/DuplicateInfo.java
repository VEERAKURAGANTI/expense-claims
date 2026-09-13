package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DuplicateInfo(
        Long claimId,
        String merchant,
        BigDecimal amount,
        LocalDate expenseDate,
        String status,
        long daysApart
) {}
