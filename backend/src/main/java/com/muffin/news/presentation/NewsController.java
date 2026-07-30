package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.explanation.NewsExplanationQueryService;
import com.muffin.news.application.query.NewsQueryService;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import com.muffin.news.presentation.swagger.NewsApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsController implements NewsApi {

    private final NewsQueryService newsQueryService;
    private final NewsExplanationQueryService newsExplanationQueryService;

    @Override
    @GetMapping
    public ApiResponse<NewsListResponse> getNews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long categoryId) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, newsQueryService.getNewsList(userId, cursor, size, categoryId));
    }

    @Override
    @GetMapping("/today")
    public ApiResponse<NewsTodayResponse> getTodayNews(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getTodayNews(userId));
    }

    @Override
    @PostMapping("/{newsId}")
    public ApiResponse<NewsDetailResponse> getNewsDetail(
            @AuthenticationPrincipal Long userId, @PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getNewsDetail(userId, newsId));
    }

    @Override
    @GetMapping("/{newsId}/sector-impacts")
    public ApiResponse<NewsSectorImpactResponse> getSectorImpacts(
            @AuthenticationPrincipal Long userId, @PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsQueryService.getSectorImpacts(newsId));
    }

    @Override
    @GetMapping("/{newsId}/explanation-cards")
    public ApiResponse<NewsExplanationCardsResponse> getExplanationCards(
            @AuthenticationPrincipal Long userId, @PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsExplanationQueryService.getExplanationCards(newsId));
    }
}
