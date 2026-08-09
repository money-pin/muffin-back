package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsExplanationCardsResponse;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsReadResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "News", description = "뉴스 API")
public interface NewsApi {

    @Operation(
            summary = "뉴스 목록 조회 (CONTENT-01-1)",
            description = "공개된 뉴스를 최신 발행순(publishedAt DESC, newsId DESC)으로 커서 페이지네이션 조회하고 현재 사용자의 스크랩 여부를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "커서가 올바르지 않거나 size가 허용 범위(1~50)를 벗어난 경우"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    ApiResponse<NewsListResponse> getNews(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "다음 목록 조회를 위한 커서. 최초 요청 시 생략") String cursor,
            @Parameter(description = "한 번에 조회할 뉴스 개수 (1~50, 기본 20)") int size,
            @Parameter(description = "조회할 카테고리 ID. 미입력 시 전체 카테고리") Long categoryId);

    @Operation(
            summary = "오늘의 뉴스 조회 (CONTENT-01-2)",
            description =
                    "한국 시간(Asia/Seoul) 기준 당일 수집된 공개 뉴스 중 경제·증권·세계 카테고리별 최신 뉴스 각 1건과 현재 사용자의 스크랩 여부를 경제·증권·세계 순서로 조회한다. 당일 공개 뉴스가 한 건도 없으면 전날 수집된 공개 뉴스를 같은 기준으로 반환한다. 선택된 날짜에 뉴스가 없는 카테고리는 응답에서 제외한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다.")
    })
    ApiResponse<NewsTodayResponse> getTodayNews(@AuthenticationPrincipal Long userId);

    @Operation(
            summary = "뉴스 상세 조회 (CONTENT-02)",
            description = "조회수와 열람 기록을 변경하지 않고 AI가 재구성한 본문과 현재 사용자의 스크랩 여부를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "아직 공개되지 않은 뉴스입니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsDetailResponse> getNewsDetail(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsId);

    @Operation(summary = "뉴스 열람 처리", description = "호출할 때마다 조회수를 1 증가시키고 사용자 열람 기록의 열람 시각을 생성 또는 갱신한 뒤 최신 조회수를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "열람 처리 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "아직 공개되지 않은 뉴스입니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsReadResponse> recordNewsRead(
            @AuthenticationPrincipal Long userId, @Parameter(description = "열람할 뉴스 ID") Long newsId);

    @Operation(
            summary = "뉴스 섹터 영향도 조회 (CONTENT-03)",
            description = "뉴스에 대한 12개 자산 섹터별 영향도를 조회한다. AI 분석 결과가 없는 섹터는 NEUTRAL로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 뉴스입니다.")
    })
    ApiResponse<NewsSectorImpactResponse> getSectorImpacts(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsId);

    @Operation(
            summary = "뉴스 해설 카드 조회 (CONTENT-04)",
            description = "뉴스 상세 페이지 경제 상식 탭에서 노출할 해설 카드 목록을 조회한다. "
                    + "DONE 상태의 카드만 cardOrder 순서로 최대 3개 반환하며, "
                    + "완료된 카드가 없으면(생성 중이거나 생성 실패) 409로 응답한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해설 카드 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 뉴스입니다. (CONTENT_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "뉴스 해설 카드 생성이 아직 완료되지 않았습니다. (CONTENT_409_001)")
    })
    ApiResponse<NewsExplanationCardsResponse> getExplanationCards(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 뉴스 ID") Long newsId);
}
