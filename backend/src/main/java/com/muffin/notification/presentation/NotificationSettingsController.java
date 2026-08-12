package com.muffin.notification.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.notification.application.NotificationSettingsCommandService;
import com.muffin.notification.application.NotificationSettingsQueryService;
import com.muffin.notification.presentation.dto.NotificationSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import com.muffin.notification.presentation.swagger.NotificationSettingsApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage/settings")
@RequiredArgsConstructor
public class NotificationSettingsController implements NotificationSettingsApi {

    private final NotificationSettingsQueryService notificationSettingsQueryService;
    private final NotificationSettingsCommandService notificationSettingsCommandService;

    @Deprecated
    @Override
    @GetMapping
    public ApiResponse<NotificationSettingsResponse> getSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingsQueryService.getSettings(userId));
    }

    @Override
    @GetMapping("/notifications")
    public ApiResponse<NotificationSettingsResponse> getNotificationSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingsQueryService.getSettings(userId));
    }

    @Override
    @PutMapping("/notifications")
    public ApiResponse<NotificationSettingsResponse> updateNotificationSettings(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid NotificationSettingsUpdateRequest request) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, notificationSettingsCommandService.updateSettings(userId, request));
    }
}
