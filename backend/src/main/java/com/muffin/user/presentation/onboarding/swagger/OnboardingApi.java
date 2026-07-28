package com.muffin.user.presentation.onboarding.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.user.presentation.onboarding.dto.CharacterResultRequest;
import com.muffin.user.presentation.onboarding.dto.CharacterResultResponse;
import com.muffin.user.presentation.onboarding.dto.OnboardingCompleteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "User", description = "닉네임/온보딩 등 사용자 API")
public interface OnboardingApi {

    @Operation(
            summary = "캐릭터 결과 저장 (AUTH-07)",
            description =
                    "온보딩 설문 응답과 선택된 muffin 타입(plain/sprinkle/butter)으로 캐릭터를 확정 저장하고, " + "캐릭터 정보와 추천 섹터 목록을 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 캐릭터입니다. (USER_404_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "이미 온보딩을 완료한 사용자입니다. (COMMON_409_001)")
    })
    ApiResponse<CharacterResultResponse> submitCharacterResult(
            @AuthenticationPrincipal Long userId, @Valid CharacterResultRequest request);

    @Operation(
            summary = "온보딩 완료 및 초기 투자금 지급 (AUTH-08)",
            description =
                    "온보딩 완료(캐릭터 결과 저장)를 확인하고 초기 투자금 100만원을 지급한다. " + "이미 지급된 사용자가 다시 호출해도 기존 자산 정보를 그대로 반환한다(멱등).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "처리 성공(신규 지급 또는 기존 자산 반환)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "캐릭터 결과 저장(온보딩)을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ApiResponse<OnboardingCompleteResponse> completeOnboarding(@AuthenticationPrincipal Long userId);
}
