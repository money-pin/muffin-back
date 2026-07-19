package com.muffin.user.presentation.onboarding.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CharacterResultRequest(
        @NotBlank String muffin,
        @NotNull @Min(1) @Max(3) Integer firstQuestion,
        @NotNull @Min(1) @Max(3) Integer secondQuestion,
        @NotNull @Min(1) @Max(3) Integer thirdQuestion) {}
