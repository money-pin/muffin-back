package com.muffin.quiz.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Quiz", description = "오늘의 한입 퀴즈 API")
public interface QuizApi {

    @Operation(
            summary = "오늘의 한입 퀴즈 조회 (QUIZ-01)",
            description = "KST 기준 오늘 공개된 퀴즈 세트와 사용자의 풀이 진행 상태를 조회한다. "
                    + "퀴즈가 없거나 공개 전이면 quizSetStatus=UNAVAILABLE과 빈 questions를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. PUBLISHED, UNAVAILABLE, NOT_STARTED, PROGRESS, FINISHED 상태를 반환한다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증이 필요합니다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "온보딩을 완료한 후 이용할 수 있습니다.")
    })
    ApiResponse<TodayQuizResponse> getTodayQuiz(@Parameter(hidden = true) Long userId);
}
