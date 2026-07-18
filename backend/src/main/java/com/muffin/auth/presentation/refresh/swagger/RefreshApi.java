package com.muffin.auth.presentation.refresh.swagger;

import com.muffin.auth.presentation.refresh.dto.RefreshResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "Refresh", description = "Access Token 재발급 API")
public interface RefreshApi {

    @Operation(
            summary = "Access/Refresh Token 재발급",
            description = "요청 Cookie의 refresh token(refreshToken)으로 access token을 새로 발급한다. "
                    + "재발급 시 refresh token도 함께 회전(rotate)되어 새 값이 Cookie로 다시 내려간다. "
                    + "요청 바디는 없으며 Authorization 헤더도 필요 없다(만료된 access token을 대체하기 위한 API이므로).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "재발급 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "refresh token이 없거나 유효하지 않습니다. (AUTH_401_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "탈퇴한 계정입니다.(AUTH_403_002) 또는 정지된 계정입니다.(AUTH_403_003)")
    })
    ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response);
}
