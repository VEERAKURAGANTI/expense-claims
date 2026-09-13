package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;

public record UserCategoryTotal(String userName, String category, BigDecimal total) {}
