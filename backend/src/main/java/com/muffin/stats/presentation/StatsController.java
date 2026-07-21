package com.muffin.stats.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.stats.application.StatsQueryService;
import com.muffin.stats.presentation.dto.ProfitHistoryRequest;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import com.muffin.stats.presentation.swagger.StatsApi;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController implements StatsApi {

    private final StatsQueryService statsQueryService;

    @Override
    @GetMapping("/summary")
    public ApiResponse<StatsSummaryResponse> getSummary(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체하고 요청에서 hidden 처리. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, statsQueryService.getSummary(userId));
    }

    @Override
    @GetMapping("/history")
    public ApiResponse<ProfitHistoryResponse> getHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @ModelAttribute ProfitHistoryRequest request) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                statsQueryService.getHistory(userId, request.toPeriod(), request.date(), request.toSort()));
    }
}
