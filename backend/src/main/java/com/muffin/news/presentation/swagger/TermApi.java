package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.SavedTermListResponse;
import com.muffin.news.presentation.dto.TermResponse;
import com.muffin.news.presentation.dto.TermSaveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "News", description = "뉴스 API")
public interface TermApi {

    @Operation(summary = "용어 사전 조회 (CONTENT-05)", description = "뉴스 본문 하이라이트 용어를 탭했을 때 바텀시트에 표시할 용어 설명을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermResponse> getTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "조회할 용어 ID") Long termId);

    @Operation(summary = "용어 저장 (CONTENT-07-1)", description = "뉴스 본문 하이라이트 또는 용어 바텀시트에서 선택한 용어를 학습 저장소에 저장한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 저장 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청이 거부되었습니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermSaveResponse> saveTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "저장할 용어 ID") Long termId);

    @Operation(summary = "용어 저장 해제 (CONTENT-07-2)", description = "학습 저장소에 저장된 용어를 해제한다. 이미 해제된 용어도 성공 상태로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "용어 저장 해제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "요청이 거부되었습니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "존재하지 않는 용어입니다.")
    })
    ApiResponse<TermSaveResponse> unsaveTerm(
            @AuthenticationPrincipal Long userId, @Parameter(description = "저장 해제할 용어 ID") Long termId);

    @Operation(
            summary = "저장한 용어 목록 조회 (MYPAGE-04-3)",
            description = "마이페이지에서 사용자가 저장한 용어 목록을 페이지 단위로 조회한다. sort=recent(기본, 최근 저장순) 또는 "
                    + "sort=alphabetical(가나다순)로 정렬할 수 있다.",
            tags = {"Mypage"})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "size가 50을 초과하거나 sort 값이 올바르지 않습니다. (COMMON_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)")
    })
    ApiResponse<SavedTermListResponse> getSavedTerms(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "페이지 번호(0부터 시작)") int page,
            @Parameter(description = "페이지 크기(최대 50)") int size,
            @Parameter(description = "정렬 기준: recent(기본) 또는 alphabetical") String sort);
}
