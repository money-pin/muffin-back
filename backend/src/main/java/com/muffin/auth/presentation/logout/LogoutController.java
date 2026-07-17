package com.muffin.auth.presentation.logout;

import com.muffin.auth.application.logout.LogoutCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.auth.presentation.logout.swagger.LogoutApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LogoutController implements LogoutApi {

    private final LogoutCommandService logoutCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        logoutCommandService.logout(userId);
        response.addHeader(
                HttpHeaders.SET_COOKIE, refreshTokenCookieHelper.expire().toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}
