package com.muffin.quiz.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.quiz.application.QuizQueryService;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import com.muffin.quiz.presentation.swagger.QuizApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController implements QuizApi {

    private final QuizQueryService quizQueryService;

    @Override
    @GetMapping("/today")
    public ApiResponse<TodayQuizResponse> getTodayQuiz(
            // TODO: 인증(Security/JWT) 구현 후 @AuthenticationPrincipal 등으로 교체. 현재는 임시 헤더.
            @RequestHeader("X-User-Id") Long userId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, quizQueryService.getTodayQuiz(userId));
    }
}
