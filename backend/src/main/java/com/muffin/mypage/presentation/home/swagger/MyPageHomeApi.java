package com.muffin.mypage.presentation.home.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "MyPageHome", description = "마이페이지 홈 조회 API")
public interface MyPageHomeApi {

    @Operation(summary = "마이페이지 홈 조회", description = "닉네임, 캐릭터, 연속 참여(스트릭)/이번 주 활동, 최근 읽은 뉴스(최대 3건)를 한 번에 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 사용자/캐릭터입니다. (USER_404_001, USER_404_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ApiResponse<MyPageHomeResponse> getHome(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
