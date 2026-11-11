package com.muffin.notification.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.notification.application.devicetoken.DeviceTokenCommandService;
import com.muffin.notification.presentation.dto.DeviceTokenRegisterRequest;
import com.muffin.notification.presentation.swagger.DeviceTokenApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DeviceTokenController implements DeviceTokenApi {

    private final DeviceTokenCommandService deviceTokenCommandService;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    @PutMapping("/api/notifications/device-token")
    public ApiResponse<Void> registerDeviceToken(
            @AuthenticationPrincipal Long userId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestBody @Valid DeviceTokenRegisterRequest request) {
        String accessToken = authorization.substring(BEARER_PREFIX.length());
        deviceTokenCommandService.register(userId, accessToken, request.token(), request.environment());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}
