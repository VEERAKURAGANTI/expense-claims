package com.bluepetal.expenseclaims.dto;

import com.bluepetal.expenseclaims.model.User;

import java.math.BigDecimal;

public record UserSummary(Long id, String name, String email, String role, BigDecimal monthlyLimit) {
    public static UserSummary from(User u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getRole().name(), u.getMonthlyLimit());
    }
}
