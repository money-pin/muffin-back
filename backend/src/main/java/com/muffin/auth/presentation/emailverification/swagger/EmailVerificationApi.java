package com.muffin.auth.presentation.emailverification.swagger;

import com.muffin.auth.presentation.emailverification.dto.EmailVerificationConfirmRequest;
import com.muffin.auth.presentation.emailverification.dto.EmailVerificationSendResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Email Verification", description = "이메일 인증번호 발송/확인 API")
public interface EmailVerificationApi {

    @Operation(
            summary = "이메일 인증번호 발송",
            description =
                    "Authorization 헤더의 access token으로 식별된 계정의 이메일로 인증번호를 발송한다. 이미 인증 완료된 계정이거나 재발송 쿨다운/일일 발송 한도를 초과하면 거부한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 정보를 찾을 수 없습니다. (COMMON_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 인증이 완료된 이메일입니다. (AUTH_409_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "재발송 쿨다운 중(AUTH_429_001) 또는 일일 발송 한도 초과(AUTH_429_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "메일 발송에 실패했습니다. (AUTH_500_001)")
    })
    ApiResponse<EmailVerificationSendResponse> sendCode(@AuthenticationPrincipal Long userId);

    @Operation(summary = "이메일 인증번호 확인", description = "가장 최근 발송된 인증번호와 대조한다. 불일치 시 시도 횟수가 누적되며, 한도 초과 시 잠긴다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인증번호가 일치하지 않습니다. (AUTH_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "인증 정보를 찾을 수 없습니다. (COMMON_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 인증이 완료된 이메일입니다. (AUTH_409_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "인증번호가 만료되었습니다. (AUTH_410_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "423",
                description = "인증 시도 횟수를 초과하여 잠겼습니다. (AUTH_423_001)")
    })
    ApiResponse<Void> verifyCode(@AuthenticationPrincipal Long userId, EmailVerificationConfirmRequest request);
}
