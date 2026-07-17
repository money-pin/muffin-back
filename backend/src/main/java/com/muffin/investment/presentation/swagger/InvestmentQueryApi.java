package com.muffin.investment.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Investment", description = "모의투자 API")
public interface InvestmentQueryApi {

    @Operation(summary = "총자산 현황 조회", description = "총자산, 최근 일간 변동과 정산 대기 여부를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "거래일 정보를 확인할 수 없음")
    })
    ApiResponse<InvestmentAssetResponse> getAsset(@Parameter(hidden = true) Long userId);

    @Operation(summary = "오늘의 모의투자 현황 조회", description = "거래일과 정산 상태에 따른 화면 상태 및 오늘 확정 내역을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "거래일 정보를 확인할 수 없음")
    })
    ApiResponse<TodayInvestmentResponse> getToday(@Parameter(hidden = true) Long userId);
}
