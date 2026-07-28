package com.muffin.quiz.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.quiz.presentation.dto.request.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.response.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.response.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.response.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultResponse;
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
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001, 온보딩 미완료 등)")
    })
    ApiResponse<TodayQuizResponse> getTodayQuiz(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "퀴즈 답안 제출 (QUIZ-02)",
            description = "사용자가 선택한 답안을 제출하고 정답 여부, 정답 선택지, 해설, 진행 상태를 반환한다. " + "이미 제출한 문항은 기존 제출 결과를 그대로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "답안 제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "해당 퀴즈 문항의 선택지가 아닙니다. (QUIZ_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001, 온보딩 미완료 등)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "오늘의 퀴즈를 아직 준비 중(QUIZ_404_001)이거나 존재하지 않는 퀴즈 문항입니다.(QUIZ_404_002)")
    })
    ApiResponse<QuizAttemptResponse> submitAnswer(
            @Parameter(hidden = true) Long userId,
            @Parameter(description = "답안을 제출할 퀴즈 문항 ID") Long quizId,
            QuizAttemptRequest request);

    @Operation(
            summary = "오늘의 한입 퀴즈 결과 조회 (QUIZ-03)",
            description = "오늘 완료한 퀴즈의 정답 수와 이미 지급된 가상 머니 보상 결과를 조회한다. "
                    + "퀴즈를 모두 완료한 경우에만 조회할 수 있으며, 보상 지급은 답안 제출 API에서 이미 처리된다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "퀴즈 결과 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001, 온보딩 미완료 등)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "오늘의 퀴즈를 아직 준비 중입니다. (QUIZ_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "퀴즈를 모두 완료한 후 결과를 조회할 수 있습니다. (QUIZ_409_001)")
    })
    ApiResponse<QuizResultResponse> getTodayQuizResult(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "지난 퀴즈 복습 목록 조회 (QUIZ-04-1)",
            description = "사용자가 완료한 지난 퀴즈 기록을 날짜별 정답 수 요약으로 조회한다. " + "조회 결과가 없으면 빈 histories를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복습 목록 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001, 온보딩 미완료 등)")
    })
    ApiResponse<QuizHistoryListResponse> getQuizHistories(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "지난 퀴즈 복습 상세 조회 (QUIZ-04-2)",
            description = "특정 날짜에 완료한 퀴즈 3문항과 선택한 답안, 정답, 해설을 조회한다. " + "조회 결과가 없으면 빈 questions를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "복습 상세 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "미래 날짜의 퀴즈 기록은 조회할 수 없습니다.(QUIZ_400_002) 또는 날짜 형식이 올바르지 않습니다.(QUIZ_400_003)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001, 온보딩 미완료 등)")
    })
    ApiResponse<QuizHistoryDetailResponse> getQuizHistoryDetail(
            @Parameter(hidden = true) Long userId, @Parameter(description = "조회할 퀴즈 날짜(yyyy-MM-dd)") String date);
}
