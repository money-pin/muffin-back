package com.muffin.notification.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.notification.presentation.dto.DeviceTokenRegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Notification", description = "마이페이지 설정/알림 API")
public interface DeviceTokenApi {

    @Operation(
            summary = "푸시 기기 토큰 등록",
            description = "APNs 기기 토큰을 등록한다. 앱 실행 시와 로그인 직후마다 호출한다. 이 기기의 토큰은 마지막으로 등록한 것만 남고, "
                    + "다른 계정에 등록된 토큰이면 이 계정으로 옮겨진다. 다른 기기에서 로그인하면 이전 기기는 로그아웃될 때까지(최대 60분) "
                    + "알림을 계속 받는다. environment는 Xcode 빌드면 SANDBOX, "
                    + "TestFlight·App Store 빌드면 PRODUCTION.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "token 누락·255자 초과, environment 누락(COMMON_400_002), "
                        + "environment가 SANDBOX/PRODUCTION이 아님(COMMON_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다(AUTH_401_001). 다른 기기에서 로그인했거나 로그아웃된 세션의 access token입니다(AUTH_401_005).")
    })
    ApiResponse<Void> registerDeviceToken(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(hidden = true) String authorization,
            @RequestBody @Valid DeviceTokenRegisterRequest request);
}
