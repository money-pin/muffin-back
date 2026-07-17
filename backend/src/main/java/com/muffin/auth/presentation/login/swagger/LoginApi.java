package com.muffin.auth.presentation.login.swagger;

import com.muffin.auth.presentation.login.dto.LocalLoginRequest;
import com.muffin.auth.presentation.login.dto.LoginResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Login", description = "로그인 API")
public interface LoginApi {

    @Operation(
            summary = "로컬 로그인",
            description = "이메일/비밀번호로 로그인하고 access token을 발급한다. refresh token은 HttpOnly Cookie로 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "이메일 또는 비밀번호가 일치하지 않습니다. (AUTH_401_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)")
    })
    ApiResponse<LoginResponse> loginLocal(LocalLoginRequest request, HttpServletResponse response);
}
