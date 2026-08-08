package com.muffin.ranking.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Ranking", description = "지난주 모의투자 랭킹 API")
public interface RankingApi {

    @Operation(
            summary = "나의 지난주 순위와 수익률 TOP 10 조회 (RANKING-01)",
            description =
                    "KST 기준 지난주 월요일~일요일 랭킹을 조회합니다. TOP 10의 캐릭터와 수익 상세를 함께 반환하며, 스냅샷 생성 전에는 CALCULATING 또는 EMPTY 상태를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    ApiResponse<WeeklyRankingResponse> getWeeklyRanking(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
