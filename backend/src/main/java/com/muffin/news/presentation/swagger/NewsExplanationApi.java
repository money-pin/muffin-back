package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "News Explanation", description = "뉴스 해설 카드 API")
public interface NewsExplanationApi {

    @Operation(
            summary = "뉴스 해설 카드 조회 (CONTENT-04)",
            description = "뉴스 상세 페이지 경제 상식 탭에서 노출할 해설 카드 목록을 조회한다. "
                    + "DONE 상태의 카드만 cardOrder 순서로 최대 3개 반환하며, "
                    + "완료된 카드가 없으면(생성 중이거나 생성 실패) 409로 응답한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해설 카드 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "뉴스 해설 카드 생성이 아직 완료되지 않았습니다.")
    })
    ApiResponse<NewsExplanationCardsResponse> getExplanationCards(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsId);
}
