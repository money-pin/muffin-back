package com.muffin.quiz.domain.quizsession;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** QuizSession 애그리거트 리포지토리 */
public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {

    Optional<QuizSession> findByUserIdAndDailyQuizSetId(Long userId, Long dailyQuizSetId);

    List<QuizSession> findAllByUserIdAndStatusOrderByDateDesc(Long userId, QuizSessionStatus status);

    Optional<QuizSession> findByUserIdAndDateAndStatus(Long userId, LocalDate date, QuizSessionStatus status);

    /** 마이페이지 홈의 스트릭(연속 참여) 계산용: 사용자가 완료(FINISHED)한 퀴즈 세션의 날짜 목록. */
    @Query("select q.date from QuizSession q where q.userId = :userId and q.status = :status")
    List<LocalDate> findDatesByUserIdAndStatus(@Param("userId") Long userId, @Param("status") QuizSessionStatus status);

    // 탈퇴 계정 데이터 정리 배치용: 아직 정리되지 않은(=quiz_session 행이 남아있는) 탈퇴 유저만 대상으로 잡아,
    // 정리가 끝난 유저는 다음 배치 실행부터 자연히 제외되게 한다.
    @Query(
            value = "select distinct q.user_id from quiz_session q "
                    + "join member m on m.user_id = q.user_id "
                    + "where m.status = 'WITHDRAWN' and m.deleted_at < :cutoff",
            nativeQuery = true)
    List<Long> findDistinctUserIdsEligibleForCleanup(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "delete from quiz_attempt where quiz_session_id in "
                    + "(select quiz_session_id from quiz_session where user_id in (:userIds))",
            nativeQuery = true)
    int deleteAttemptsByUserIdIn(@Param("userIds") List<Long> userIds);

    @Modifying(clearAutomatically = true)
    @Query(value = "delete from quiz_session where user_id in (:userIds)", nativeQuery = true)
    int deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
