package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;

public record OverLimitUser(String name, BigDecimal spend, BigDecimal monthlyLimit) {}
