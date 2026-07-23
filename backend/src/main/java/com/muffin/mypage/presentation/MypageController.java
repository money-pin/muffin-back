package com.muffin.mypage.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.mypage.application.MypageScrapQueryService;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.swagger.MypageApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController implements MypageApi {

    private final MypageScrapQueryService mypageScrapQueryService;

    @Override
    @GetMapping("/scraps")
    public ApiResponse<ScrapListResponse> getScraps(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, mypageScrapQueryService.getScraps(userId, sort, cursor, size));
    }
}
