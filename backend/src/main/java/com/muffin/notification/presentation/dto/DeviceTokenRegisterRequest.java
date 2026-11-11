package com.muffin.notification.presentation.dto;

import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DeviceTokenRegisterRequest(
        @NotBlank @Size(max = 255) String token, @NotNull ApnsEnvironment environment) {}
