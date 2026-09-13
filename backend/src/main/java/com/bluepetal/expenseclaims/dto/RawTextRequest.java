package com.bluepetal.expenseclaims.dto;

import jakarta.validation.constraints.NotBlank;

public record RawTextRequest(@NotBlank String rawText) {}
