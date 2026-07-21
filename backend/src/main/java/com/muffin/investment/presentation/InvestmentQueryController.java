package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.InvestmentQueryService;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.swagger.InvestmentQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentQueryController implements InvestmentQueryApi {

    private final InvestmentQueryService investmentQueryService;

    @Override
    @GetMapping("/asset")
    public ApiResponse<InvestmentAssetResponse> getAsset(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, investmentQueryService.getAsset(userId));
    }

    @Override
    @GetMapping("/today")
    public ApiResponse<TodayInvestmentResponse> getToday(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, investmentQueryService.getToday(userId));
    }
}
