package com.muffin.user.presentation.onboarding;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.user.application.onboarding.CharacterResultCommandService;
import com.muffin.user.application.onboarding.OnboardingCompletionService;
import com.muffin.user.presentation.onboarding.dto.CharacterResultRequest;
import com.muffin.user.presentation.onboarding.dto.CharacterResultResponse;
import com.muffin.user.presentation.onboarding.dto.OnboardingCompleteResponse;
import com.muffin.user.presentation.onboarding.swagger.OnboardingApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController implements OnboardingApi {

    private final CharacterResultCommandService characterResultCommandService;
    private final OnboardingCompletionService onboardingCompletionService;

    @Override
    @PostMapping("/character")
    public ApiResponse<CharacterResultResponse> submitCharacterResult(
            @AuthenticationPrincipal Long userId, @Valid @RequestBody CharacterResultRequest request) {
        CharacterResultResponse response = characterResultCommandService.submit(userId, request);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @PutMapping("/completion")
    public ApiResponse<OnboardingCompleteResponse> completeOnboarding(@AuthenticationPrincipal Long userId) {
        OnboardingCompleteResponse response = onboardingCompletionService.complete(userId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
