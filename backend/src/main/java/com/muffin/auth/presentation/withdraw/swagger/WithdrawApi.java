package com.muffin.auth.presentation.withdraw.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Withdraw", description = "회원 탈퇴 API")
public interface WithdrawApi {

    @Operation(
            summary = "회원 탈퇴",
            description = "Authorization 헤더의 access token으로 식별된 계정을 탈퇴 처리한다. 이름은 즉시 삭제되고 이메일은 복구 불가능한 값으로 대체되며, "
                    + "refresh token이 무효화되고 refresh token Cookie가 만료(Max-Age=0)된다. 투자/퀴즈 기록은 탈퇴 6개월 후 별도 배치가 삭제한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "탈퇴 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 탈퇴했거나 정지된 계정이라 탈퇴할 수 없습니다. (COMMON_409_001)")
    })
    ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response);
}
