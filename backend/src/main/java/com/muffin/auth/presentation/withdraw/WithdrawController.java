package com.muffin.auth.presentation.withdraw;

import com.muffin.auth.application.withdraw.WithdrawCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.auth.presentation.withdraw.swagger.WithdrawApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class WithdrawController implements WithdrawApi {

    private final WithdrawCommandService withdrawCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @DeleteMapping("/account")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        withdrawCommandService.withdraw(userId);
        response.addHeader(
                HttpHeaders.SET_COOKIE, refreshTokenCookieHelper.expire().toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}
