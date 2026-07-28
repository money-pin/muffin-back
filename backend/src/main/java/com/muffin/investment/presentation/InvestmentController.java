package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.InvestmentCommandResult;
import com.muffin.investment.application.InvestmentCommandService;
import com.muffin.investment.application.InvestmentQueryService;
import com.muffin.investment.application.settlement.SettlementQueryService;
import com.muffin.investment.presentation.dto.InvestmentAssetResponse;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.swagger.InvestmentApi;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentController implements InvestmentApi {

    private final InvestmentCommandService investmentCommandService;
    private final InvestmentQueryService investmentQueryService;
    private final SettlementQueryService settlementQueryService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<TodayInvestmentResponse>> confirm(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody InvestmentRequest request) {
        InvestmentCommandResult result = investmentCommandService.confirm(userId, request);
        GeneralSuccessCode code = result.created() ? GeneralSuccessCode.CREATED : GeneralSuccessCode.OK;
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.onSuccess(code, result.response()));
    }

    @Override
    @PatchMapping("/today")
    public ApiResponse<TodayInvestmentResponse> updateToday(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody InvestmentRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, investmentCommandService.updateToday(userId, request));
    }

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

    @Override
    @GetMapping("/settlement/result")
    public ApiResponse<SettlementResultResponse> getSettlementResult(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, settlementQueryService.getRecentSettlementResult(userId));
    }
}
