package com.muffin.mypage.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.mypage.application.MypageRecentNewsQueryService;
import com.muffin.mypage.application.MypageScrapQueryService;
import com.muffin.mypage.application.home.MyPageHomeQueryService;
import com.muffin.mypage.presentation.dto.RecentNewsResponse;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
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
    private final MyPageHomeQueryService myPageHomeQueryService;

    @Override
    @GetMapping("/api/mypage/home")
    public ApiResponse<MyPageHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
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
}
