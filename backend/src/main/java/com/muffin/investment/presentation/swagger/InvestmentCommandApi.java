package com.muffin.investment.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

public interface InvestmentCommandApi {

    @Operation(summary = "투자 확정", description = "오늘의 섹터별 투자 수량을 확정한다. 동일 요청 재전송은 기존 결과를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "최초 확정"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "동일 요청 재전송"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "투자 시간·섹터·예산 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "다른 구성으로 이미 확정됨")
    })
    ResponseEntity<ApiResponse<TodayInvestmentResponse>> confirm(
            @Parameter(hidden = true) Long userId, @Valid InvestmentRequest request);

    @Operation(summary = "오늘 투자 수정", description = "자정 마감 전 오늘 확정한 섹터 구성을 전체 교체한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "투자 시간·섹터·예산 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "오늘 확정한 투자 없음")
    })
    ApiResponse<TodayInvestmentResponse> updateToday(
            @Parameter(hidden = true) Long userId, @Valid InvestmentRequest request);
}
