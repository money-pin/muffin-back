package com.muffin.notification.application.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizReminderNotificationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private final QuizSetRepository quizSetRepository = mock(QuizSetRepository.class);
    private final QuizSessionRepository quizSessionRepository = mock(QuizSessionRepository.class);
    private final NotificationRecipientQueryRepository recipientQueryRepository =
            mock(NotificationRecipientQueryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final QuizReminderNotificationService service = new QuizReminderNotificationService(
            quizSetRepository, quizSessionRepository, recipientQueryRepository, dispatcher);

    @Test
    @DisplayName("오늘 퀴즈를 끝낸 사용자는 빼고 보낸다")
    void excludesFinishedUsers() {
        when(quizSetRepository.findByQuizDateAndStatus(TODAY, QuizSetStatus.PUBLISHED))
                .thenReturn(Optional.of(mock(QuizSet.class)));
        when(quizSessionRepository.findUserIdsByDateAndStatus(TODAY, QuizSessionStatus.FINISHED))
                .thenReturn(List.of(2L));
        when(recipientQueryRepository.findRecipientUserIds(NotificationType.QUIZ))
                .thenReturn(List.of(1L, 2L, 3L));
        when(dispatcher.dispatchAll(any(), anyString(), anyList(), any(PushMessage.class)))
                .thenReturn(NotificationDispatchSummary.empty());

        service.remind(TODAY);

        verify(dispatcher)
                .dispatchAll(NotificationType.QUIZ, "2026-10-20", List.of(1L, 3L), NotificationMessages.quizReminder());
    }

    @Test
    @DisplayName("오늘 퀴즈가 공개되지 않았으면 보내지 않는다")
    void skipsWithoutPublishedQuiz() {
        when(quizSetRepository.findByQuizDateAndStatus(TODAY, QuizSetStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        NotificationDispatchSummary summary = service.remind(TODAY);

        assertThat(summary.skipReason()).isEqualTo("no_published_quiz");
        verify(dispatcher, never()).dispatchAll(any(), anyString(), anyList(), any(PushMessage.class));
        verify(recipientQueryRepository, never()).findRecipientUserIds(eq(NotificationType.QUIZ));
    }
}
