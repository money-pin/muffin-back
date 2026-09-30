package com.muffin.briefing.presentation;

import com.muffin.briefing.application.query.BriefingQueryService;
import com.muffin.briefing.presentation.dto.BriefingDateListResponse;
import com.muffin.briefing.presentation.dto.BriefingResponse;
import com.muffin.briefing.presentation.dto.BriefingViewResponse;
import com.muffin.briefing.presentation.swagger.BriefingApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/briefings")
@RequiredArgsConstructor
public class BriefingController implements BriefingApi {

    private final BriefingQueryService briefingQueryService;

    @Override
    @GetMapping("/today")
    public ApiResponse<BriefingResponse> getTodayBriefing() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, briefingQueryService.getTodayBriefing());
    }

    @Override
    @GetMapping("/dates")
    public ApiResponse<BriefingDateListResponse> getRecentBriefingDates() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, briefingQueryService.getRecentBriefingDates());
    }

    @Override
    @GetMapping("/{briefingDate}")
    public ApiResponse<BriefingResponse> getBriefing(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate briefingDate) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, briefingQueryService.getBriefing(briefingDate));
    }

    @Override
    @PostMapping("/{briefingDate}/views")
    public ApiResponse<BriefingViewResponse> recordBriefingView(
            @AuthenticationPrincipal Long userId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate briefingDate) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, briefingQueryService.recordView(userId, briefingDate));
    }
}
