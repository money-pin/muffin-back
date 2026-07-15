package com.muffin.auth.presentation.emailverification;

import com.muffin.auth.application.emailverification.EmailVerificationCommandService;
import com.muffin.auth.presentation.emailverification.dto.EmailVerificationConfirmRequest;
import com.muffin.auth.presentation.emailverification.dto.EmailVerificationSendRequest;
import com.muffin.auth.presentation.emailverification.swagger.EmailVerificationApi;
import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email")
@RequiredArgsConstructor
public class EmailVerificationController implements EmailVerificationApi {

    private final EmailVerificationCommandService emailVerificationCommandService;

    @Override
    @PostMapping("/verification")
    public ApiResponse<Void> sendCode(@Valid @RequestBody EmailVerificationSendRequest request) {
        emailVerificationCommandService.sendCode(request.email());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @Override
    @PostMapping("/verification/confirm")
    public ApiResponse<Void> verifyCode(@Valid @RequestBody EmailVerificationConfirmRequest request) {
        emailVerificationCommandService.verifyCode(request.email(), request.code());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }
}
