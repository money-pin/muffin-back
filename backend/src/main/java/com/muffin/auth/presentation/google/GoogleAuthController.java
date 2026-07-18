package com.muffin.auth.presentation.google;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.google.GoogleAuthCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.auth.presentation.google.dto.GoogleAuthRequest;
import com.muffin.auth.presentation.google.dto.GoogleAuthResponse;
import com.muffin.auth.presentation.google.swagger.GoogleAuthApi;
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
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class GoogleAuthController implements GoogleAuthApi {

    private final GoogleAuthCommandService googleAuthCommandService;
    private final RefreshTokenCookieHelper refreshTokenCookieHelper;

    @Override
    @PostMapping("/google")
    public ApiResponse<GoogleAuthResponse> authenticate(
            @Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenPair result = googleAuthCommandService.authenticate(request.idToken(), request.termsAgreed());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieHelper.build(result.refreshToken()).toString());

        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new GoogleAuthResponse(result.accessToken()));
    }
}
