package com.muffin.mypage.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.mypage.presentation.dto.RecentNewsResponse;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@Tag(name = "Mypage", description = "마이페이지 API")
public interface MypageApi {

    @Operation(
            summary = "마이페이지 홈 조회 (MYPAGE-01)",
            description = "닉네임, 캐릭터, 연속 참여(스트릭)/이번 주 활동, 최근 읽은 뉴스(최대 3건)를 한 번에 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "존재하지 않는 사용자/캐릭터입니다. (USER_404_001, USER_404_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ApiResponse<MyPageHomeResponse> getHome(@Parameter(hidden = true) @AuthenticationPrincipal Long userId);

    @Operation(
            summary = "스크랩한 뉴스 목록 조회 (MYPAGE-04-1)",
            description = "마이페이지에서 스크랩한 뉴스를 지정한 정렬(최근 저장순/업로드순/조회수순)로 커서 페이지네이션 조회한다. "
                    + "sort 미지정 시 SAVED_DESC(최근 저장순), size 미지정 시 10. nextCursor는 다음 페이지가 있을 때만 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 스크랩이 없으면 items는 빈 배열이고 hasNext=false, nextCursor는 생략된다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "size/sort/cursor 등 페이지 요청 값이 올바르지 않음. (MYPAGE_400_004)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자 정보를 찾을 수 없습니다. (MYPAGE_404_001)")
    })
    ApiResponse<ScrapListResponse> getScraps(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "정렬 기준. SAVED_DESC/PUBLISHED_DESC/VIEW_DESC (기본 SAVED_DESC)") String sort,
            @Parameter(description = "페이지네이션 커서(이전 응답의 nextCursor)") String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)") int size);

    @Operation(
            summary = "최근 읽은 뉴스 목록 조회 (MYPAGE-04-2)",
            description = "마이페이지에서 최근 읽은 뉴스를 열람 시각(read_at) 최신순으로 커서 페이지네이션 조회한다. "
                    + "size 미지정 시 10. nextCursor는 다음 페이지가 있을 때만 내려간다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 읽은 뉴스가 없으면 items는 빈 배열이고 hasNext=false, nextCursor는 생략된다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "size/cursor 등 페이지 요청 값이 올바르지 않음. (MYPAGE_400_004)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증에 실패했습니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "사용자 정보를 찾을 수 없습니다. (MYPAGE_404_001)")
    })
    ApiResponse<RecentNewsResponse> getRecentNews(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "페이지네이션 커서(이전 응답의 nextCursor)") String cursor,
            @Parameter(description = "페이지 크기 (1~50, 기본 10)") int size);
}
