package com.muffin.scrap.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.scrap.application.ScrapCommandService;
import com.muffin.scrap.presentation.dto.ScrapResponse;
import com.muffin.scrap.presentation.swagger.ScrapApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news/{newsId}/scrap")
@RequiredArgsConstructor
public class ScrapController implements ScrapApi {

    private final ScrapCommandService scrapCommandService;

    @Override
    @PutMapping
    public ApiResponse<ScrapResponse> scrap(@AuthenticationPrincipal Long userId, @PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, scrapCommandService.scrap(userId, newsId));
    }

    @Override
    @DeleteMapping
    public ApiResponse<ScrapResponse> unscrap(@AuthenticationPrincipal Long userId, @PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, scrapCommandService.unscrap(userId, newsId));
    }
}
