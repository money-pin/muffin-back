package com.muffin.stats.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Stats", description = "수익 통계 API")
public interface StatsApi {

    @Operation(
            summary = "수익 통계 조회 (STATS-02)",
            description =
                    "가입 이후 누적 손익, 최근 7일 누적 수익률 그래프, 수익 TOP3 섹터, 투자 성향을 한 번에 조회한다. 정산 완료(SETTLED) 투자만 집계하며, 정산 이력이 없으면 빈 상태로 응답한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 정산 이력이 없으면 investDate/investmentType은 생략되고 graph/topSectors는 빈 배열이다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했습니다.")
    })
    ApiResponse<StatsSummaryResponse> getSummary(@Parameter(hidden = true) Long userId);
}
