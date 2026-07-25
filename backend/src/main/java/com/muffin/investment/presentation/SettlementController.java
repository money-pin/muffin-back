package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.settlement.SettlementQueryService;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import com.muffin.investment.presentation.swagger.SettlementApi;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class SettlementController implements SettlementApi {

    private final SettlementQueryService settlementQueryService;

    @Override
    @GetMapping("/settlement/result")
    public ApiResponse<SettlementResultResponse> getSettlementResult(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, settlementQueryService.getRecentSettlementResult(userId));
    }
}
