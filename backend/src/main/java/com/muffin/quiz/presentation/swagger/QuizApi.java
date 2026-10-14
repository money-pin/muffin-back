package com.muffin.quiz.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.quiz.presentation.dto.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.QuizResultResponse;
import com.muffin.quiz.presentation.dto.TodayQuizResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Quiz", description = "오늘의 한입 퀴즈 API")
public interface QuizApi {

    @Operation(
            summary = "오늘의 한입 퀴즈 조회 (QUIZ-01)",
            description = "KST 기준 오늘 공개된 퀴즈 세트와 사용자의 풀이 진행 상태를 조회한다. "
                    + "퀴즈가 없거나 생성 중·공개 전·생성 실패이면 HTTP 200의 quizSetStatus=UNAVAILABLE과 빈 questions를 반환한다. "
                    + "모의투자 확정 성공(최초 201, 동일 구성 재요청 200) 후 구매 완료 팝업의 확인을 누르면 이 API를 새로 조회한다. "
                    + "result.quizSetStatus=PUBLISHED이고 result.sessionStatus=NOT_STARTED인 경우에만 "
                    + "'퀴즈를 풀면 최대 30만원을 더 받을 수 있어요' 안내 팝업을 표시한다. 오늘 퀴즈를 한 문제도 풀지 않은 사용자만 대상이다. "
                    + "PROGRESS·FINISHED·UNAVAILABLE·조회 실패에서는 팝업을 생략하며, 퀴즈 조회 실패가 투자 성공 결과에 영향을 주면 안 된다. "
                    + "퀴즈 화면 진입 시 다시 조회하고 progress.nextQuestionOrder와 일치하는 questions[].quizOrder부터 시작하거나 이어 푼다. "
                    + "완료 또는 이용 불가이면 nextQuestionOrder는 null이다. HTTP 성공 여부·정답 수·보상 지급 여부로 노출을 판단하지 않는다. "
                    + "오늘은 Asia/Seoul 기준이며 quizDate를 사용한다. 날짜 또는 사용자가 바뀌면 이전 응답을 재사용하지 않는다. "
                    + "같은 투자 완료 이벤트에서는 한 번만 표시하고 화면 이탈 후 도착한 응답은 무시한다. "
                    + "최대 30만원은 하루 퀴즈 전체의 가상 머니 보상 상한이며 남은 보상액이나 확정 지급액이 아니다. "
                    + "팝업 표시·닫기·이동 자체로 보상을 지급하지 않는다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. PUBLISHED, UNAVAILABLE, NOT_STARTED, PROGRESS, FINISHED 상태를 반환한다."),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ApiResponse<TodayQuizResponse> getTodayQuiz(@Parameter(hidden = true) Long userId);

    @Operation(
            summary = "퀴즈 답안 제출 (QUIZ-02)",
            description = "사용자가 선택한 답안을 제출하고 정답 여부, 정답 선택지, 해설, 진행 상태를 반환한다. " + "이미 제출한 문항은 기존 제출 결과를 그대로 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "답안 제출 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "해당 퀴즈 문항의 선택지가 아닙니다. (QUIZ_400_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "인증이 필요합니다. (AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "요청이 거부되었습니다. (AUTH_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "오늘의 퀴즈를 아직 준비 중(QUIZ_404_001)이거나 존재하지 않는 퀴즈 문항입니다.(QUIZ_404_002)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ResponseEntity<ApiResponse<QuizAttemptResponse>> submitAnswer(
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
                description = "요청이 거부되었습니다. (AUTH_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "오늘의 퀴즈를 아직 준비 중입니다. (QUIZ_404_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다.(USER_409_001) 또는 퀴즈를 모두 완료한 후 결과를 조회할 수 있습니다.(QUIZ_409_001)")
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
                description = "요청이 거부되었습니다. (AUTH_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
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
                description = "요청이 거부되었습니다. (AUTH_403_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "온보딩을 먼저 완료해야 합니다. (USER_409_001)")
    })
    ApiResponse<QuizHistoryDetailResponse> getQuizHistoryDetail(
            @Parameter(hidden = true) Long userId, @Parameter(description = "조회할 퀴즈 날짜(yyyy-MM-dd)") String date);
}
