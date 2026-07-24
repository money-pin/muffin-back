package com.muffin.news.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.news.presentation.dto.response.SavedTermListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "MyPageSavedTerms", description = "마이페이지 저장한 용어 목록 API")
public interface SavedTermApi {

    @Operation(
            summary = "저장한 용어 목록 조회",
            description = "마이페이지에서 사용자가 저장한 용어 목록을 페이지 단위로 조회한다. sort=recent(기본, 최근 저장순) 또는 "
                    + "sort=alphabetical(가나다순)로 정렬할 수 있다.")
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
