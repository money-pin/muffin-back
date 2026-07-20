package com.muffin.user.presentation.nickname;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.user.application.nickname.NicknameQueryService;
import com.muffin.user.presentation.nickname.dto.NicknameCheckResponse;
import com.muffin.user.presentation.nickname.swagger.NicknameApi;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class NicknameController implements NicknameApi {

    private final NicknameQueryService nicknameQueryService;

    @Override
    @GetMapping("/nickname/check")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @AuthenticationPrincipal Long userId, @RequestParam @NotBlank String nickname) {
        boolean available = nicknameQueryService.isAvailable(nickname);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new NicknameCheckResponse(available));
    }
}
