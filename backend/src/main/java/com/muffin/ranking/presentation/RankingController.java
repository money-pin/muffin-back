package com.muffin.ranking.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.ranking.application.WeeklyRankingQueryService;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import com.muffin.ranking.presentation.swagger.RankingApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController implements RankingApi {

    private final WeeklyRankingQueryService weeklyRankingQueryService;

    @Override
    @GetMapping("/weekly")
    public ApiResponse<WeeklyRankingResponse> getWeeklyRanking(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, weeklyRankingQueryService.getWeeklyRanking(userId));
    }
}
