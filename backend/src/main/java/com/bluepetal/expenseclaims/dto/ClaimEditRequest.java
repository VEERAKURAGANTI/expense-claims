package com.bluepetal.expenseclaims.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClaimEditRequest(
        String merchant,
        @NotNull BigDecimal amount,
        @NotNull String category,
        @NotNull LocalDate expenseDate,
        String description
) {}
