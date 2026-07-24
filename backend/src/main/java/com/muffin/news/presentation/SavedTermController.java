package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.response.SavedTermListResponse;
import com.muffin.news.presentation.swagger.SavedTermApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mypage/saved-terms")
@RequiredArgsConstructor
public class SavedTermController implements SavedTermApi {

    private final TermQueryService termQueryService;

    @Override
    @GetMapping
    public ApiResponse<SavedTermListResponse> getSavedTerms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, termQueryService.getSavedTerms(userId, PageRequest.of(page, size), sort));
    }
}
