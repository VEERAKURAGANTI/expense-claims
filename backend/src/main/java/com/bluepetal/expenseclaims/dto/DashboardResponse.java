package com.bluepetal.expenseclaims.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        List<ClaimResponse> drafts,
        List<ClaimResponse> inFlight,
        List<ClaimResponse> history,
        BigDecimal unpaidTotal,
        BigDecimal monthSpend,
        BigDecimal monthlyLimit,
        String monthKey
) {}
