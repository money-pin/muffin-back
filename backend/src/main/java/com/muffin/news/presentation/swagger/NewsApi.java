package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "News", description = "뉴스 조회 API")
public interface NewsApi {

    @Operation(
            summary = "뉴스 목록 조회",
            description = "공개된 뉴스를 최신 발행순(publishedAt DESC, newsId DESC)으로 커서 페이지네이션 조회한다. 인증이 필요 없다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "커서가 올바르지 않거나 size가 허용 범위(1~50)를 벗어난 경우")
    })
    ApiResponse<NewsListResponse> getNews(
            @Parameter(description = "다음 목록 조회를 위한 커서. 최초 요청 시 생략") String cursor,
            @Parameter(description = "한 번에 조회할 뉴스 개수 (1~50, 기본 20)") int size,
            @Parameter(description = "조회할 카테고리 ID. 미입력 시 전체 카테고리") Long categoryId);

    @Operation(summary = "오늘의 뉴스 조회", description = "한국 시간(Asia/Seoul) 기준 당일 수집된 공개 뉴스 중 최신 발행순 상위 3건을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    ApiResponse<NewsTodayResponse> getTodayNews(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "뉴스 상세 조회",
            description = "AI가 재구성한 본문과 스크랩 여부를 조회한다. 조회 시 조회수 증가와 열람 기록 갱신이 함께 일어나므로 POST를 사용한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "아직 공개되지 않은 뉴스입니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsDetailResponse> getNewsDetail(
            @Parameter(hidden = true) Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsSummaryId);

    @Operation(summary = "뉴스 섹터 영향도 조회", description = "뉴스에 대한 12개 자산 섹터별 영향도를 조회한다. AI 분석 결과가 없는 섹터는 NEUTRAL로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsSectorImpactResponse> getSectorImpacts(
            @Parameter(hidden = true) Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsSummaryId);
}
