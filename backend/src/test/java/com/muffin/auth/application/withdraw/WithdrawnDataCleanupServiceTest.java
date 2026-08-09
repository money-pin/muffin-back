package com.muffin.auth.application.withdraw;

import static org.assertj.core.api.Assertions.*;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 후 보관 기간(6개월)이 지난 유저의 투자/퀴즈 기록만 삭제하고, 아직 기간이 안 지났거나 탈퇴하지 않은 유저의 데이터는
 * 남기는지 검증한다. deleted_at은 User.withdraw()가 현재 시각으로 고정해서 도메인 API로 과거 시점을 만들 수 없으므로
 * 네이티브 쿼리로 되돌린다(backdateDeletedAt). 이 쿼리가 활성 트랜잭션을 요구해 클래스 전체를 @Transactional로 감싼다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WithdrawnDataCleanupServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private WithdrawnDataCleanupService withdrawnDataCleanupService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private QuizSessionRepository quizSessionRepository;

    @Autowired
    private EntityManager entityManager;

    private User createWithdrawnUser(LocalDateTime deletedAt) {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        user.withdraw();
        userRepository.save(user);
        backdateDeletedAt(user, deletedAt);
        return user;
    }

    private void backdateDeletedAt(User user, LocalDateTime deletedAt) {
        entityManager
                .createNativeQuery("UPDATE member SET deleted_at = :deletedAt WHERE user_id = :userId")
                .setParameter("deletedAt", deletedAt)
                .setParameter("userId", user.getUserId())
                .executeUpdate();
        entityManager.clear();
    }

    private Investment createInvestment(Long userId) {
        Investment investment = Investment.confirm(userId, LocalDate.now());
        investment.addSector(1L, 1, 10_000L, BigDecimal.TEN);
        return investmentRepository.save(investment);
    }

    private QuizSession createQuizSession(Long userId) {
        QuizSession session = QuizSession.start(userId, 1L, LocalDate.now(), 3);
        session.recordAttempt(1L, 1L, true, 100L, LocalDateTime.now());
        return quizSessionRepository.save(session);
    }

    @Test
    @DisplayName("탈퇴 6개월이 지난 유저의 투자/퀴즈 기록은 삭제되지만 User 자신은 남는다")
    void cleanupWithdrawnUserData_deletesExpiredWithdrawnUserData() {
        User user = createWithdrawnUser(LocalDateTime.now(KST).minusMonths(6).minusDays(1));
        Investment investment = createInvestment(user.getUserId());
        QuizSession session = createQuizSession(user.getUserId());

        withdrawnDataCleanupService.cleanupWithdrawnUserData();

        assertThat(investmentRepository.findById(investment.getId())).isEmpty();
        assertThat(quizSessionRepository.findById(session.getId())).isEmpty();
        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    @Test
    @DisplayName("탈퇴한 지 아직 6개월이 안 된 유저의 투자/퀴즈 기록은 남는다")
    void cleanupWithdrawnUserData_keepsDataForNotYetExpiredWithdrawal() {
        User user = createWithdrawnUser(LocalDateTime.now(KST).minusDays(1));
        Investment investment = createInvestment(user.getUserId());
        QuizSession session = createQuizSession(user.getUserId());

        withdrawnDataCleanupService.cleanupWithdrawnUserData();

        assertThat(investmentRepository.findById(investment.getId())).isPresent();
        assertThat(quizSessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    @DisplayName("탈퇴하지 않은(ACTIVE) 유저의 투자/퀴즈 기록은 건드리지 않는다")
    void cleanupWithdrawnUserData_keepsActiveUserData() {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        Investment investment = createInvestment(user.getUserId());
        QuizSession session = createQuizSession(user.getUserId());

        withdrawnDataCleanupService.cleanupWithdrawnUserData();

        assertThat(investmentRepository.findById(investment.getId())).isPresent();
        assertThat(quizSessionRepository.findById(session.getId())).isPresent();
    }
}
