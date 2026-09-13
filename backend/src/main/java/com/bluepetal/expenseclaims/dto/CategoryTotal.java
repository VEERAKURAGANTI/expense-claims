package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;

public record CategoryTotal(String category, long count, BigDecimal total) {}
