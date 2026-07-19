package com.muffin.investment.domain.investment;

import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Investment 애그리거트 루트 리포지토리. 내부 엔티티(InvestmentSector)는 루트를 통해 저장/조회되므로 별도 리포지토리를 두지 않는다. */
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    /** 정산 대상 조회: 지정 상태들(CONFIRMED/NO_INVEST) + 정산 상태(PENDING/FAILED) + 투자일자 이전. 오케스트레이터가 대상을 뽑는 용도(섹터는 처리기에서 재조회). */
    List<Investment> findByStatusInAndSettlementStatusInAndInvestDateLessThan(
            Collection<InvestmentStatus> statuses,
            Collection<SettlementStatus> settlementStatuses,
            LocalDate investDate);

    /** 유저별 처리기에서 섹터까지 함께 로딩해 정산한다(N+1 방지). */
    @EntityGraph(attributePaths = "sectors")
    Optional<Investment> findWithSectorsById(Long id);

    // TODO : 확인필요 - 이슈 #23 오늘 투자 및 처리 대기 상태 조회를 위해 기존 Repository에 추가함.
    @EntityGraph(attributePaths = "sectors")
    Optional<Investment> findWithSectorsByUserIdAndInvestDateAndStatus(
            Long userId, LocalDate investDate, InvestmentStatus status);

    // TODO : 확인필요 - 이슈 #40 멱등 POST와 PATCH-자정 마감 경합 처리를 위해 기존 Repository 조회를 확장함.
    @EntityGraph(attributePaths = "sectors")
    Optional<Investment> findWithSectorsByUserIdAndInvestDate(Long userId, LocalDate investDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "sectors")
    @Query("select i from Investment i where i.userId = :userId and i.investDate = :investDate")
    Optional<Investment> findWithSectorsForUpdate(
            @Param("userId") Long userId, @Param("investDate") LocalDate investDate);

    List<Investment> findByUserIdAndSettlementStatusInAndInvestDateLessThan(
            Long userId, Collection<SettlementStatus> settlementStatuses, LocalDate investDate);

    // TODO : 확인필요 - 이슈 #40 과거 미마감 날짜 복구를 위해 기존 Repository에 날짜별 마감 완료 건수 조회를 추가함.
    /** 지정 투자일에 사용자별 자정 마감이 완전히 끝난 건수. NO_INVEST는 마감 시각, 확정 투자는 마감 시각과 모든 매수가를 확인한다. */
    @Query(
            value = "select count(*) from investment i "
                    + "where i.invest_date = :investDate and i.finalized_at is not null "
                    + "and (i.status = 'NO_INVEST' or not exists ("
                    + "select 1 from investment_sector s "
                    + "where s.investment_id = i.investment_id and s.buy_price is null))",
            nativeQuery = true)
    long countCompletedFinalizationsByInvestDate(@Param("investDate") LocalDate investDate);

    // 탈퇴 계정 데이터 정리 배치용: 아직 정리되지 않은(=investment 행이 남아있는) 탈퇴 유저만 대상으로 잡아,
    // 정리가 끝난 유저는 다음 배치 실행부터 자연히 제외되게 한다.
    @Query(
            value = "select distinct i.user_id from investment i "
                    + "join member m on m.user_id = i.user_id "
                    + "where m.status = 'WITHDRAWN' and m.deleted_at < :cutoff",
            nativeQuery = true)
    List<Long> findDistinctUserIdsEligibleForCleanup(@Param("cutoff") LocalDateTime cutoff);

    @Modifying(clearAutomatically = true)
    @Query(
            value = "delete from investment_sector where investment_id in "
                    + "(select investment_id from investment where user_id in (:userIds))",
            nativeQuery = true)
    int deleteSectorsByUserIdIn(@Param("userIds") List<Long> userIds);

    @Modifying(clearAutomatically = true)
    @Query(value = "delete from investment where user_id in (:userIds)", nativeQuery = true)
    int deleteAllByUserIdIn(@Param("userIds") List<Long> userIds);
}
