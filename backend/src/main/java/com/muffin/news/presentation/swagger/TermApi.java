package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.response.TermResponse;
import com.muffin.news.presentation.dto.response.TermSaveResponse;
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

    @Operation(summary = "용어 저장", description = "뉴스 본문 하이라이트 또는 용어 바텀시트에서 선택한 용어를 학습 저장소에 저장한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청이 거부되었습니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermSaveResponse> saveTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "저장할 용어 ID") Long termId);

    @Operation(summary = "용어 저장 해제", description = "학습 저장소에 저장된 용어를 해제한다. 이미 해제된 용어도 성공 상태로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 저장 해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청이 거부되었습니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermSaveResponse> unsaveTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "저장 해제할 용어 ID") Long termId);
}
