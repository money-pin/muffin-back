package com.muffin.auth.presentation.signup.swagger;

import com.muffin.auth.presentation.signup.dto.LocalSignupRequest;
import com.muffin.auth.presentation.signup.dto.SignupResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Signup", description = "회원가입 API")
public interface SignupApi {

    @Operation(
            summary = "로컬 회원가입",
            description = "이메일/비밀번호로 회원가입하고 즉시 로그인 상태로 access token을 발급한다. refresh token은 HttpOnly Cookie로 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "가입 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 값 검증 실패(COMMON_400_002), 약관 미동의(AUTH_400_002), 비밀번호 형식 오류(COMMON_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 이메일입니다. (AUTH_409_001)")
    })
    ApiResponse<SignupResponse> signupLocal(LocalSignupRequest request, HttpServletResponse response);
}
