package com.muffin.auth.presentation.swagger;

import com.muffin.auth.presentation.dto.EmailVerificationConfirmRequest;
import com.muffin.auth.presentation.dto.EmailVerificationSendRequest;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Email Verification", description = "이메일 인증번호 발송/확인 API")
public interface EmailVerificationApi {

    @Operation(summary = "이메일 인증번호 발송", description = "입력한 이메일로 인증번호를 발송한다. 이미 가입된 이메일이거나 재발송 쿨다운/일일 발송 한도를 초과하면 거부한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발송 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 이메일입니다. (AUTH_409_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "재발송 쿨다운 중(AUTH_429_001) 또는 일일 발송 한도 초과(AUTH_429_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "메일 발송에 실패했습니다. (AUTH_500_001)")
    })
    ApiResponse<Void> sendCode(EmailVerificationSendRequest request);

    @Operation(summary = "이메일 인증번호 확인", description = "가장 최근 발송된 인증번호와 대조한다. 불일치 시 시도 횟수가 누적되며, 한도 초과 시 잠긴다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "인증번호가 일치하지 않습니다. (AUTH_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "410",
                description = "인증번호가 만료되었습니다. (AUTH_410_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "423",
                description = "인증 시도 횟수를 초과하여 잠겼습니다. (AUTH_423_001)")
    })
    ApiResponse<Void> verifyCode(EmailVerificationConfirmRequest request);
}
