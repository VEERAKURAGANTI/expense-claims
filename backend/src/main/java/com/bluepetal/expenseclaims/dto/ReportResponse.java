package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReportResponse(
        String monthKey,
        BigDecimal totalSpend,
        List<CategoryTotal> byCategory,
        List<UserCategoryTotal> byUserCategory,
        List<OverLimitUser> overLimit
) {}
