package com.muffin.quiz.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.quiz.application.QuizCommandService;
import com.muffin.quiz.application.QuizQueryService;
import com.muffin.quiz.presentation.dto.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.QuizResultResponse;
import com.muffin.quiz.presentation.dto.TodayQuizResponse;
import com.muffin.quiz.presentation.swagger.QuizApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController implements QuizApi {

    private final QuizQueryService quizQueryService;
    private final QuizCommandService quizCommandService;

    @Override
    @GetMapping("/today")
    public ApiResponse<TodayQuizResponse> getTodayQuiz(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getTodayQuiz(userId));
    }

    @Override
    @PostMapping("/{quizId}/attempts")
    public ResponseEntity<ApiResponse<QuizAttemptResponse>> submitAnswer(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizAttemptRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        GeneralSuccessCode.CREATED, quizCommandService.submitAnswer(userId, quizId, request)));
    }

    @Override
    @GetMapping("/today/result")
    public ApiResponse<QuizResultResponse> getTodayQuizResult(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getTodayQuizResult(userId));
    }

    @Override
    @GetMapping("/history")
    public ApiResponse<QuizHistoryListResponse> getQuizHistories(@AuthenticationPrincipal Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getQuizHistories(userId));
    }

    @Override
    @GetMapping("/history/{date}")
    public ApiResponse<QuizHistoryDetailResponse> getQuizHistoryDetail(
            @AuthenticationPrincipal Long userId, @PathVariable String date) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getQuizHistoryDetail(userId, date));
    }
}
