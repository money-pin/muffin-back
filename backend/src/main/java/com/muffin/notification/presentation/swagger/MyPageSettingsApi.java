package com.muffin.notification.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.notification.presentation.dto.MyPageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "MyPageSettings", description = "마이페이지 설정 조회/변경 API")
public interface MyPageSettingsApi {

    @Operation(summary = "마이페이지 설정 조회", description = "마이페이지 설정 화면에 필요한 값을 조회한다. 현재는 알림 설정만 포함한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<MyPageSettingsResponse> getSettings(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(summary = "알림 설정 조회", description = "알림 설정 4개 토글의 현재 상태를 조회한다. 최초 조회 시 기본값(모두 true)이 자동 생성된다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<MyPageSettingsResponse> getNotificationSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(summary = "알림 설정 변경", description = "알림 설정 4개 토글을 한 번에 갱신한다(전체 교체, 부분 갱신 아님).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증에 실패했습니다. (COMMON_400_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<MyPageSettingsResponse> updateNotificationSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody @Valid NotificationSettingsUpdateRequest request);
}
