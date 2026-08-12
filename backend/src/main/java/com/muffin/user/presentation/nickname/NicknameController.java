package com.muffin.user.presentation.nickname;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.user.application.nickname.NicknameCommandService;
import com.muffin.user.application.nickname.NicknameQueryService;
import com.muffin.user.presentation.nickname.dto.NicknameChangeRequest;
import com.muffin.user.presentation.nickname.dto.NicknameChangeResponse;
import com.muffin.user.presentation.nickname.dto.NicknameCheckResponse;
import com.muffin.user.presentation.nickname.swagger.NicknameApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class NicknameController implements NicknameApi {

    private final NicknameQueryService nicknameQueryService;
    private final NicknameCommandService nicknameCommandService;

    @Override
    @GetMapping("/nicknames/availability")
    public ApiResponse<NicknameCheckResponse> checkNickname(
            @AuthenticationPrincipal Long userId, @RequestParam @NotBlank String nickname) {
        boolean available = nicknameQueryService.isAvailable(nickname);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new NicknameCheckResponse(available));
    }

    @Override
    @PatchMapping("/nickname")
    public ApiResponse<NicknameChangeResponse> changeNickname(
            @AuthenticationPrincipal Long userId, @RequestBody @Valid NicknameChangeRequest request) {
        String nickname = nicknameCommandService.changeNickname(userId, request.nickname());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, new NicknameChangeResponse(nickname));
    }
}
