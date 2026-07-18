package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "News Explanation", description = "뉴스 해설 카드 API")
public interface NewsExplanationApi {

    @Operation(
            summary = "뉴스 해설 카드 조회 (CONTENT-04)",
            description = "뉴스 상세 페이지 경제 상식 탭에서 노출할 해설 카드 목록을 조회한다. "
                    + "DONE 상태의 카드만 cardOrder 순서로 최대 3개 반환하며, 카드가 없으면 빈 목록을 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해설 카드 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsExplanationCardsResponse> getExplanationCards(@Parameter(description = "조회할 뉴스 ID") Long newsId);
}
