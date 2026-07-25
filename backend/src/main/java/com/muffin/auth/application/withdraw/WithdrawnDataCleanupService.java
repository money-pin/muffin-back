package com.muffin.auth.application.withdraw;

import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 후 보관 기간(기본 6개월)이 지난 계정의 투자/퀴즈 기록을 삭제하는 배치. User/Auth 자체는 건드리지 않고 영구 보관한다(이미 탈퇴
 * 시점에 이름/이메일이 비식별화되어 있음).
 *
 * <p>투자/퀴즈 각각 "아직 행이 남아있는 탈퇴 유저"만 대상으로 조회하므로, 정리가 끝난 유저는 다음 배치 실행부터 자연히
 * 대상에서 빠진다(User 테이블에 별도 정리 완료 플래그를 둘 필요가 없다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnDataCleanupService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final InvestmentRepository investmentRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final WithdrawnDataCleanupProperties properties;

    @Transactional
    public void cleanupWithdrawnUserData() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusMonths(properties.retentionMonths());

        List<Long> investmentUserIds = investmentRepository.findDistinctUserIdsEligibleForCleanup(cutoff);
        if (!investmentUserIds.isEmpty()) {
            investmentRepository.deleteSectorsByUserIdIn(investmentUserIds);
            investmentRepository.deleteAllByUserIdIn(investmentUserIds);
        }

        List<Long> quizUserIds = quizSessionRepository.findDistinctUserIdsEligibleForCleanup(cutoff);
        if (!quizUserIds.isEmpty()) {
            quizSessionRepository.deleteAttemptsByUserIdIn(quizUserIds);
            quizSessionRepository.deleteAllByUserIdIn(quizUserIds);
        }

        log.info(
                "[withdrawn-data-cleanup] investmentUsers={}, quizUsers={}",
                investmentUserIds.size(),
                quizUserIds.size());
    }
}
