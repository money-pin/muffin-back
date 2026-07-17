package com.muffin.auth.presentation.google.swagger;

import com.muffin.auth.presentation.google.dto.GoogleAuthRequest;
import com.muffin.auth.presentation.google.dto.GoogleAuthResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Google Auth", description = "구글 OAuth 통합(가입/로그인) API")
public interface GoogleAuthApi {

    @Operation(
            summary = "구글 로그인/가입",
            description = "구글 ID Token을 검증해 기존 계정이면 로그인, 없으면 termsAgreed를 검사해 자동 가입한다. "
                    + "access token은 응답 바디로, refresh token은 HttpOnly Cookie로 발급한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인/가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "약관에 동의해야 가입할 수 있습니다. (AUTH_400_002, 신규 가입 시에만)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "유효하지 않은 구글 로그인 토큰입니다. (AUTH_401_004)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "최근 탈퇴한 이메일입니다.(AUTH_409_003, 신규 가입 시에만)")
    })
    ApiResponse<GoogleAuthResponse> authenticate(GoogleAuthRequest request, HttpServletResponse response);
}
