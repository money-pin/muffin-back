package com.muffin.scrap.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.scrap.presentation.dto.ScrapResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Scrap", description = "뉴스 스크랩 API")
public interface ScrapApi {

    @Operation(
            summary = "뉴스 스크랩 (CONTENT-06-1)",
            description = "관심 있는 뉴스를 저장한다. 멱등 연산이라 이미 스크랩한 뉴스에 다시 요청해도 최초 저장 시각을 유지한 채 성공한다. "
                    + "공개(PUBLISHED)된 뉴스만 스크랩할 수 있다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "스크랩 성공. isScrapped=true, scrappedAt은 최초 저장 시각(KST)."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "아직 공개되지 않은 뉴스입니다. (CONTENT_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 뉴스입니다. (CONTENT_404_001)")
    })
    ApiResponse<ScrapResponse> scrap(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "스크랩할 뉴스 ID") Long newsId);

    @Operation(
            summary = "뉴스 스크랩 해제 (CONTENT-06-2)",
            description = "뉴스 스크랩을 해제한다. 멱등 연산이라 스크랩하지 않은 뉴스에 요청해도 성공한다. " + "공개 상태와 무관하게 이미 저장한 스크랩은 해제할 수 있다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "해제 성공. isScrapped=false."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 뉴스입니다. (CONTENT_404_001)")
    })
    ApiResponse<ScrapResponse> unscrap(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "스크랩 해제할 뉴스 ID") Long newsId);
}
