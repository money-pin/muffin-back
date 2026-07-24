package com.muffin.auth.presentation.refresh;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.application.refresh.RefreshCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.auth.presentation.refresh.dto.RefreshResponse;
import com.muffin.auth.presentation.refresh.swagger.RefreshApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class RefreshController implements RefreshApi {

    private final RefreshCommandService refreshCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @PostMapping("/token/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = refreshTokenCookieHelper
                .extract(request)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        TokenPair result = refreshCommandService.refresh(rawRefreshToken);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new RefreshResponse(result.accessToken()));
    }
}
