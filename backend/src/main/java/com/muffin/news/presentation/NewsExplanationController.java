package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.explanation.NewsExplanationQueryService;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import com.muffin.news.presentation.swagger.NewsExplanationApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
public class NewsExplanationController implements NewsExplanationApi {

    private final NewsExplanationQueryService newsExplanationQueryService;

    @Override
    @GetMapping("/{newsId}/explanation-cards")
    // TODO: Security/JWT 적용 후 인증 사용자만 접근 가능하도록 보호 엔드포인트로 전환한다.
    public ApiResponse<NewsExplanationCardsResponse> getExplanationCards(@PathVariable Long newsId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, newsExplanationQueryService.getExplanationCards(newsId));
    }
}
