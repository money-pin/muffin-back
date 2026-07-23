package com.muffin.user.presentation.nickname.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.user.presentation.nickname.dto.NicknameChangeRequest;
import com.muffin.user.presentation.nickname.dto.NicknameChangeResponse;
import com.muffin.user.presentation.nickname.dto.NicknameCheckResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Nickname", description = "닉네임 조회/변경 API")
public interface NicknameApi {

    @Operation(
            summary = "닉네임 중복 조회",
            description = "온보딩/마이페이지에서 입력한 닉네임이 이미 사용 중인지 확인한다. 형식(2~10자, 한글/영문/숫자/공백) 위반이나 비속어 포함 시 400을 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "닉네임 형식이 올바르지 않습니다.(COMMON_400_001) 또는 비속어가 포함되어 있습니다.(USER_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<NicknameCheckResponse> checkNickname(
            @AuthenticationPrincipal Long userId, @Parameter(description = "중복 확인할 닉네임") @NotBlank String nickname);

    @Operation(summary = "닉네임 변경", description = "마이페이지에서 사용자의 닉네임을 변경한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "닉네임 형식이 올바르지 않습니다.(COMMON_400_001) 또는 비속어가 포함되어 있습니다.(USER_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 사용 중인 닉네임입니다. (USER_409_002)")
    })
    ApiResponse<NicknameChangeResponse> changeNickname(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid NicknameChangeRequest request);
}
