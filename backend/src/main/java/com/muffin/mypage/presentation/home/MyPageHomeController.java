package com.muffin.mypage.presentation.home;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.mypage.application.home.MyPageHomeQueryService;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
import com.muffin.mypage.presentation.home.swagger.MyPageHomeApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage/home")
@RequiredArgsConstructor
public class MyPageHomeController implements MyPageHomeApi {

    private final MyPageHomeQueryService myPageHomeQueryService;

    @Override
    @GetMapping
    public ApiResponse<MyPageHomeResponse> getHome(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, myPageHomeQueryService.getHome(userId));
    }
}
