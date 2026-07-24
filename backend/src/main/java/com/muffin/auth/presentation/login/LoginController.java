package com.muffin.auth.presentation.login;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.login.LoginCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.auth.presentation.login.dto.LocalLoginRequest;
import com.muffin.auth.presentation.login.dto.LoginResponse;
import com.muffin.auth.presentation.login.swagger.LoginApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class LoginController implements LoginApi {

    private final LoginCommandService loginCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @PostMapping("/login")
    public ApiResponse<LoginResponse> loginLocal(
            @Valid @RequestBody LocalLoginRequest request, HttpServletResponse response) {
        TokenPair result = loginCommandService.loginLocal(request.email(), request.password());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new LoginResponse(result.accessToken()));
    }
}
