package com.muffin.quiz.application.generation;

import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyQuizPublicationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QuizSetRepository quizSetRepository;

    /** 오늘 발행 대기 상태인 퀴즈 세트를 사용자에게 공개한다. */
    @Transactional
    public boolean publishToday() {
        return publish(LocalDate.now(KST), LocalDateTime.now(KST));
    }

    /** 지정한 날짜의 READY 퀴즈 세트를 PUBLISHED 상태로 전환한다. */
    @Transactional
    public boolean publish(LocalDate quizDate, LocalDateTime publishedAt) {
        return quizSetRepository
                .findByQuizDateAndStatus(quizDate, QuizSetStatus.READY)
                .map(quizSet -> publish(quizSet, publishedAt))
                .orElseGet(() -> {
                    log.warn("Daily quiz publication skipped: READY quiz set not found, quizDate={}", quizDate);
                    return false;
                });
    }

    private boolean publish(QuizSet quizSet, LocalDateTime publishedAt) {
        quizSet.publish(publishedAt);
        log.info("Daily quiz publication completed: quizDate={} quizSetId={}", quizSet.getQuizDate(), quizSet.getId());
        return true;
    }
}
