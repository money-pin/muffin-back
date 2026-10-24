package com.muffin.notification.application.send;

import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** NOTI-02 퀴즈 리마인드. 오늘의 퀴즈를 아직 끝내지 않은 사용자에게만 보낸다. */
@Service
@RequiredArgsConstructor
public class QuizReminderNotificationService {

    private final QuizSetRepository quizSetRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final NotificationRecipientQueryRepository recipientQueryRepository;
    private final NotificationDispatcher dispatcher;

    public NotificationDispatchSummary remind(LocalDate quizDate) {
        if (quizSetRepository
                .findByQuizDateAndStatus(quizDate, QuizSetStatus.PUBLISHED)
                .isEmpty()) {
            return NotificationDispatchSummary.skipped("no_published_quiz");
        }

        Set<Long> finished =
                new HashSet<>(quizSessionRepository.findUserIdsByDateAndStatus(quizDate, QuizSessionStatus.FINISHED));
        List<Long> recipients = recipientQueryRepository.findRecipientUserIds(NotificationType.QUIZ).stream()
                .filter(userId -> !finished.contains(userId))
                .toList();

        return dispatcher.dispatchAll(
                NotificationType.QUIZ, quizDate.toString(), recipients, NotificationMessages.quizReminder());
    }
}
