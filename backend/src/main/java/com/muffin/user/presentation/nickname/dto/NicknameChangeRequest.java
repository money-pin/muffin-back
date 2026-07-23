package com.muffin.user.presentation.nickname.dto;

import jakarta.validation.constraints.NotBlank;

public record NicknameChangeRequest(@NotBlank String nickname) {}
