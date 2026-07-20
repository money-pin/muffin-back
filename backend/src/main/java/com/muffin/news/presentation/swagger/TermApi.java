package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.response.TermResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Term", description = "경제 용어 사전 API")
public interface TermApi {

    @Operation(summary = "용어 사전 조회 (CONTENT-05)", description = "뉴스 본문 하이라이트 용어를 탭했을 때 바텀시트에 표시할 용어 설명을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermResponse> getTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 용어 ID") Long termId);
}
