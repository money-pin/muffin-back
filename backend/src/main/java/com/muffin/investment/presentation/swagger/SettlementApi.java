package com.muffin.investment.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Investment", description = "모의투자 API")
public interface SettlementApi {

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
