package com.muffin.investment.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.investment.application.SettlementQueryService;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import com.muffin.investment.presentation.swagger.SettlementApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체하고 요청에서 hidden 처리. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, settlementQueryService.getRecentSettlementResult(userId));
    }
}
