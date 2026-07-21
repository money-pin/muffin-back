package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.InvestmentCommandResult;
import com.muffin.investment.application.InvestmentCommandService;
import com.muffin.investment.presentation.dto.InvestmentRequest;
import com.muffin.investment.presentation.dto.TodayInvestmentResponse;
import com.muffin.investment.presentation.swagger.InvestmentCommandApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investments")
@RequiredArgsConstructor
public class InvestmentCommandController implements InvestmentCommandApi {

    private final InvestmentCommandService investmentCommandService;

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
}
