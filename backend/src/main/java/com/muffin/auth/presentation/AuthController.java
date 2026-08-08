package com.muffin.auth.presentation;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.google.GoogleAuthCommandService;
import com.muffin.auth.application.login.LoginCommandService;
import com.muffin.auth.application.logout.LogoutCommandService;
import com.muffin.auth.application.refresh.RefreshCommandService;
import com.muffin.auth.application.signup.SignupCommandService;
import com.muffin.auth.application.withdraw.WithdrawCommandService;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.presentation.google.dto.GoogleAuthRequest;
import com.muffin.auth.presentation.google.dto.GoogleAuthResponse;
import com.muffin.auth.presentation.login.dto.LocalLoginRequest;
import com.muffin.auth.presentation.login.dto.LoginResponse;
import com.muffin.auth.presentation.refresh.dto.RefreshResponse;
import com.muffin.auth.presentation.signup.dto.LocalSignupRequest;
import com.muffin.auth.presentation.signup.dto.SignupResponse;
import com.muffin.auth.presentation.swagger.AuthApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증(회원가입/로그인/구글로그인/토큰재발급/로그아웃/탈퇴) 컨트롤러.
 *
 * <p>로그인 전에도 열어야 하는 엔드포인트({@code /auth/**})와 인증이 필요한 엔드포인트({@code /api/auth/**})가
 * 섞여 있어 클래스 레벨 {@code @RequestMapping} 없이 메서드마다 전체 경로를 명시한다({@link
 * com.muffin.global.config.SecurityConfig} 참고).
 */
@RestController
@RequiredArgsConstructor
public class AuthController implements AuthApi {

    private final SignupCommandService signupCommandService;
    private final LoginCommandService loginCommandService;
    private final GoogleAuthCommandService googleAuthCommandService;
    private final RefreshCommandService refreshCommandService;
    private final LogoutCommandService logoutCommandService;
    private final WithdrawCommandService withdrawCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @PostMapping("/auth/signup")
    public ApiResponse<SignupResponse> signupLocal(
            @Valid @RequestBody LocalSignupRequest request, HttpServletResponse response) {
        TokenPair result = signupCommandService.signupLocal(
                request.email(), request.password(), request.name(), request.termsAgreed());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new SignupResponse(result.accessToken()));
    }

    @Override
    @PostMapping("/auth/login")
    public ApiResponse<LoginResponse> loginLocal(
            @Valid @RequestBody LocalLoginRequest request, HttpServletResponse response) {
        TokenPair result = loginCommandService.loginLocal(request.email(), request.password());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new LoginResponse(result.accessToken()));
    }

    @Override
    @PostMapping("/auth/token/refresh")
    public ApiResponse<RefreshResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawRefreshToken = refreshTokenCookieHelper
                .extract(request)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        TokenPair result = refreshCommandService.refresh(rawRefreshToken);

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new RefreshResponse(result.accessToken()));
    }

    @Override
    @PostMapping("/api/auth/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        logoutCommandService.logout(userId);
        response.addHeader(
                HttpHeaders.SET_COOKIE, refreshTokenCookieHelper.expire().toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @Override
    @DeleteMapping("/api/auth/account")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId, HttpServletResponse response) {
        withdrawCommandService.withdraw(userId);
        response.addHeader(
                HttpHeaders.SET_COOKIE, refreshTokenCookieHelper.expire().toString());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @Override
    @PostMapping("/auth/google")
    public ApiResponse<GoogleAuthResponse> authenticateGoogle(
            @Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenPair result = googleAuthCommandService.authenticate(request.idToken());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new GoogleAuthResponse(result.accessToken()));
    }
}
