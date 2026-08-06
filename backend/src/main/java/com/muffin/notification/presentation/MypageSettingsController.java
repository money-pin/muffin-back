package com.muffin.notification.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.notification.application.NotificationSettingsCommandService;
import com.muffin.notification.application.NotificationSettingsQueryService;
import com.muffin.notification.presentation.dto.MypageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import com.muffin.notification.presentation.swagger.MypageSettingsApi;
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
public class MypageSettingsController implements MypageSettingsApi {

    private final NotificationSettingsQueryService notificationSettingsQueryService;
    private final NotificationSettingsCommandService notificationSettingsCommandService;

    @Override
    @GetMapping
    public ApiResponse<MypageSettingsResponse> getSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingsQueryService.getSettings(userId));
    }

    @Override
    @GetMapping("/notifications")
    public ApiResponse<MypageSettingsResponse> getNotificationSettings(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationSettingsQueryService.getSettings(userId));
    }

    @Override
    @PutMapping("/notifications")
    public ApiResponse<MypageSettingsResponse> updateNotificationSettings(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid NotificationSettingsUpdateRequest request) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, notificationSettingsCommandService.updateSettings(userId, request));
    }
}
