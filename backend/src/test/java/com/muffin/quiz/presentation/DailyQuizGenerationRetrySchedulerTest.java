package com.muffin.quiz.presentation;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyQuizGenerationRetrySchedulerTest {

    private final DailyQuizGenerationService dailyQuizGenerationService = mock(DailyQuizGenerationService.class);
    private final DailyQuizGenerationRetryScheduler scheduler =
            new DailyQuizGenerationRetryScheduler(dailyQuizGenerationService);

    @Test
    @DisplayName("재시도 스케줄러는 오늘 퀴즈 생성을 호출한다")
    void retryDailyQuizGeneration_callsGenerationService() {
        scheduler.retryDailyQuizGeneration();

        verify(dailyQuizGenerationService).generateToday();
    }

    @Test
    @DisplayName("재시도 스케줄러는 생성 서비스 예외를 밖으로 전파하지 않는다")
    void retryDailyQuizGeneration_doesNotThrowWhenGenerationFails() {
        doThrow(new IllegalStateException("generation failed"))
                .when(dailyQuizGenerationService)
                .generateToday();

        scheduler.retryDailyQuizGeneration();

        verify(dailyQuizGenerationService).generateToday();
    }
}
