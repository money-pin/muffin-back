package com.muffin.investment.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Investment", description = "모의투자 API")
public interface InvestmentApi {

    @Operation(summary = "투자 확정 (INVEST-03-1)", description = "오늘의 섹터별 투자 수량을 확정한다. 동일 요청 재전송은 기존 결과를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "최초 확정"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동일 요청 재전송"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 검증 또는 투자 가능 시간·섹터·예산 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "다른 구성으로 이미 확정했거나 사용자 자산이 초기화되지 않음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "거래일 정보를 확인할 수 없음(SECTOR_503_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)")
    })
    ResponseEntity<ApiResponse<TodayInvestmentResponse>> confirm(
            @Parameter(hidden = true) Long userId, @Valid InvestmentRequest request);

    @Operation(summary = "오늘 투자 수정 (INVEST-03-3)", description = "자정 마감 전 오늘 확정한 섹터 구성을 전체 교체한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "요청 검증 또는 투자 가능 시간·섹터·예산 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "오늘 확정한 투자 없음(INVESTMENT_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "사용자 자산이 초기화되지 않음(INVESTMENT_409_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "거래일 정보를 확인할 수 없음(SECTOR_503_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)")
    })
    ApiResponse<TodayInvestmentResponse> updateToday(
            @Parameter(hidden = true) Long userId, @Valid InvestmentRequest request);

    @Operation(summary = "총자산 현황 조회 (INVEST-01)", description = "총자산, 최근 일간 변동과 정산 대기 여부를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "거래일 정보를 확인할 수 없음(SECTOR_503_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)")
    })
    ApiResponse<InvestmentAssetResponse> getAsset(@Parameter(hidden = true) Long userId);

    @Operation(summary = "오늘의 모의투자 현황 조회 (INVEST-03-2)", description = "거래일과 정산 상태에 따른 화면 상태 및 오늘 확정 내역을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "503",
                description = "거래일 정보를 확인할 수 없음(SECTOR_503_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)")
    })
    ApiResponse<TodayInvestmentResponse> getToday(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "정산 결과 팝업 조회 (INVEST-06)",
            description =
                    "가장 최근 정산 대상 투자의 결과를 조회한다. 정산 완료(SETTLED)면 손익 결과를, 정산 중이면 SETTLEMENT_PENDING, 투자/정산 결과가 없으면 NO_INVESTMENT 사유를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 결과(SETTLED) 또는 사유(reason)를 반환한다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증에 실패했습니다.")
    })
    ApiResponse<SettlementResultResponse> getSettlementResult(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId);
}
