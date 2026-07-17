package com.muffin.quiz.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.quiz.application.QuizCommandService;
import com.muffin.quiz.application.QuizQueryService;
import com.muffin.quiz.presentation.dto.request.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.response.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultResponse;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import com.muffin.quiz.presentation.swagger.QuizApi;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    public ApiResponse<TodayQuizResponse> getTodayQuiz(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getTodayQuiz(userId));
    }

    @Override
    @GetMapping("/today/result")
    public ApiResponse<QuizResultResponse> getTodayQuizResult(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getTodayQuizResult(userId));
    }

    @Override
    @PostMapping("/{quizId}/attempt")
    public ApiResponse<QuizAttemptResponse> submitAnswer(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizAttemptRequest request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizCommandService.submitAnswer(userId, quizId, request));
    }
}
