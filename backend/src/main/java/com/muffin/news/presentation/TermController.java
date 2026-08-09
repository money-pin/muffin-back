package com.muffin.news.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.news.application.term.TermCommandService;
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.SavedTermListResponse;
import com.muffin.news.presentation.dto.TermResponse;
import com.muffin.news.presentation.dto.TermSaveResponse;
import com.muffin.news.presentation.swagger.TermApi;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TermController implements TermApi {

    private final TermQueryService termQueryService;
    private final TermCommandService termCommandService;

    @Override
    @GetMapping("/api/terms/{termId}")
    public ApiResponse<TermResponse> getTerm(@AuthenticationPrincipal Long userId, @PathVariable Long termId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, termQueryService.getTerm(userId, termId));
    }

    @Override
    @PutMapping("/api/terms/{termId}/saved-term")
    public ApiResponse<TermSaveResponse> saveTerm(@AuthenticationPrincipal Long userId, @PathVariable Long termId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, termCommandService.saveTerm(userId, termId));
    }

    @Override
    @DeleteMapping("/api/terms/{termId}/saved-term")
    public ApiResponse<TermSaveResponse> unsaveTerm(@AuthenticationPrincipal Long userId, @PathVariable Long termId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, termCommandService.unsaveTerm(userId, termId));
    }

    @Override
    @GetMapping("/api/mypage/saved-terms")
    public ApiResponse<SavedTermListResponse> getSavedTerms(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, termQueryService.getSavedTerms(userId, PageRequest.of(page, size), sort));
    }
}
