package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.query.NewsQueryService;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import com.muffin.news.presentation.swagger.NewsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController implements NewsApi {

    private final NewsQueryService newsQueryService;

    @Override
    @GetMapping
    public ApiResponse<NewsListResponse> getNews(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getNewsList(cursor, size, categoryId));
    }

    @Override
    @GetMapping("/today")
    public ApiResponse<NewsTodayResponse> getTodayNews(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체하고 요청에서 hidden 처리. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getTodayNews());
    }

    @Override
    @PostMapping("/{newsSummaryId}")
    public ApiResponse<NewsDetailResponse> getNewsDetail(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long newsSummaryId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getNewsDetail(userId, newsSummaryId));
    }

    @Override
    @GetMapping("/{newsSummaryId}/sector-impacts")
    public ApiResponse<NewsSectorImpactResponse> getSectorImpacts(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId, @PathVariable Long newsSummaryId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getSectorImpacts(newsSummaryId));
    }
}
