package com.muffin.auth.presentation.logout.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Logout", description = "로그아웃 API")
public interface LogoutApi {

    @Operation(
            summary = "로그아웃",
            description = "Authorization 헤더의 access token으로 식별된 계정의 refresh token을 무효화하고, "
                    + "refresh token Cookie를 만료시킨다(Max-Age=0). 이미 로그아웃된 상태에서 다시 호출해도 200을 반환한다(멱등).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response);
}
