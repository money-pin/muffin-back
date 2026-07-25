package com.muffin.stats.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryRequest;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse;
import com.muffin.stats.presentation.dto.RecentDetailResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
    ApiResponse<StatsSummaryResponse> getSummary(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(
            summary = "누적 수익 내역 조회 (STATS-08)",
            description = "기간 탭(일/주/월/년/전체) 단위로 그 기간에 발생한 누적 손익을 요약하고, 종목(섹터)별 내역을 원하는 기준으로 정렬해 조회한다. "
                    + "정산 완료(SETTLED) 투자만 집계한다. 손익률은 해당 기간(또는 섹터)의 누적 매수금 대비 값이다. "
                    + "date는 period 형식에 맞아야 하며(DAY=YYYY-MM-DD, WEEK=YYYY-Www(ISO 주차), MONTH=YYYY-MM, YEAR=YYYY, ALL=불필요), "
                    + "미지정 시 해당 period의 현재(오늘/이번 주/이번 달/올해, KST)로 처리한다. hasPrev/hasNext는 윈도우 밖에 데이터가 있는지로 판단한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 기간 내 정산 데이터가 없으면 summary는 0, sectors는 빈 배열이다. period=ALL이면 date는 생략된다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "period 값 오류/누락(STATS_400_001), sort 값 오류(STATS_400_002), date 형식이 period와 맞지 않음(STATS_400_003)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)")
    })
    ApiResponse<ProfitHistoryResponse> getHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @ParameterObject ProfitHistoryRequest request);

    @Operation(
            summary = "최근 투자 성과 상세 (STATS-03)",
            description = "가장 최근 정산 완료(SETTLED)된 투자 1건을 섹터별로 상세하게 조회한다. 각 섹터의 매수금/손익금/손익률과, 거래정지 등으로 0%가 적용된 폴백 여부"
                    + "(isFallback)를 함께 내려준다. 손익률은 그 섹터 매수금 대비 값(소수 첫째자리)이다. "
                    + "정산 이력이 없으면 date는 null, 금액은 0, sectors는 빈 배열이다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 정산 이력이 없으면 date는 null, totalInvestment/profitAmount는 0, sectors는 빈 배열이다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)")
    })
    ApiResponse<RecentDetailResponse> getRecentDetail(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
