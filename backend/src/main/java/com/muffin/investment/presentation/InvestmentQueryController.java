package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.InvestmentQueryService;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.swagger.InvestmentQueryApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentQueryController implements InvestmentQueryApi {

    private final InvestmentQueryService investmentQueryService;

    @Override
    @GetMapping("/asset")
    public ApiResponse<InvestmentAssetResponse> getAsset(
            // TODO: 인증 구현 후 인증 컨텍스트에서 사용자 ID를 조회하도록 교체한다.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, investmentQueryService.getAsset(userId));
    }

    @Override
    @GetMapping("/today")
    public ApiResponse<TodayInvestmentResponse> getToday(
            // TODO: 인증 구현 후 인증 컨텍스트에서 사용자 ID를 조회하도록 교체한다.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, investmentQueryService.getToday(userId));
    }
}
