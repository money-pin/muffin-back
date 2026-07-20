package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.response.TermResponse;
import com.muffin.news.presentation.swagger.TermApi;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/terms")
@RequiredArgsConstructor
public class TermController implements TermApi {

    private final TermQueryService termQueryService;

    @Override
    @GetMapping("/{termId}")
    public ApiResponse<TermResponse> getTerm(@AuthenticationPrincipal Long userId, @PathVariable Long termId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, termQueryService.getTerm(userId, termId));
    }
}
