package com.bluepetal.expenseclaims.dto;

import java.util.List;

public record ClaimDetailResponse(ClaimResponse claim, DuplicateInfo duplicate, List<AuditEntryResponse> audit) {}
