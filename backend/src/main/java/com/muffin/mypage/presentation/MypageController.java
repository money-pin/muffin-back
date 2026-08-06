package com.muffin.mypage.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.mypage.application.MypageRecentNewsQueryService;
import com.muffin.mypage.application.MypageScrapQueryService;
import com.muffin.mypage.application.home.MypageHomeQueryService;
import com.muffin.mypage.application.quizhistory.MypageQuizHistoryQueryService;
import com.muffin.mypage.presentation.dto.RecentNewsResponse;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse;
import com.muffin.mypage.presentation.quizhistory.dto.MypageQuizHistoryResponse;
import com.muffin.mypage.presentation.swagger.MypageApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MypageController implements MypageApi {

    private final MypageScrapQueryService mypageScrapQueryService;
    private final MypageRecentNewsQueryService mypageRecentNewsQueryService;
    private final MypageHomeQueryService myPageHomeQueryService;
    private final MypageQuizHistoryQueryService mypageQuizHistoryQueryService;

    @Override
    @GetMapping("/api/mypage/home")
    public ApiResponse<MypageHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, myPageHomeQueryService.getHome(userId));
    }

    @Override
    @GetMapping("/api/mypage/scraps")
    public ApiResponse<ScrapListResponse> getScraps(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, mypageScrapQueryService.getScraps(userId, sort, cursor, size));
    }

    @Override
    @GetMapping("/api/mypage/recent-news")
    public ApiResponse<RecentNewsResponse> getRecentNews(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, mypageRecentNewsQueryService.getRecentNews(userId, cursor, size));
    }

    @Override
    @GetMapping("/api/mypage/quiz-history")
    public ApiResponse<MypageQuizHistoryResponse> getQuizHistory(
            @AuthenticationPrincipal Long userId, @RequestParam int year, @RequestParam int month) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                MypageQuizHistoryResponse.from(
                        year, month, mypageQuizHistoryQueryService.getQuizHistory(userId, year, month)));
    }
}
