package com.muffin.user.presentation.nickname.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.user.presentation.nickname.dto.NicknameCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Nickname", description = "닉네임 중복 조회 API")
public interface NicknameApi {

    @Operation(summary = "닉네임 중복 조회", description = "온보딩 중 사용자가 입력한 닉네임이 이미 사용 중인지 확인한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<NicknameCheckResponse> checkNickname(
            @AuthenticationPrincipal Long userId, @Parameter(description = "중복 확인할 닉네임") @NotBlank String nickname);
}
