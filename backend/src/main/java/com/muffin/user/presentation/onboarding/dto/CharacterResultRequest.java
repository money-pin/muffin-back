package com.muffin.user.presentation.onboarding.dto;

import com.muffin.character.domain.enums.MuffinType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CharacterResultRequest(
        @NotNull MuffinType muffin,
        @NotNull @Min(1) @Max(3) Integer firstQuestion,
        @NotNull @Min(1) @Max(3) Integer secondQuestion,
        @NotNull @Min(1) @Max(3) Integer thirdQuestion) {}
