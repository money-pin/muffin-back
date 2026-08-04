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

    @EntityGraph(attributePaths = "sectors")
    Optional<Investment> findWithSectorsByUserIdAndInvestDateAndStatus(
            Long userId, LocalDate investDate, InvestmentStatus status);

    @EntityGraph(attributePaths = "sectors")
    Optional<Investment> findWithSectorsByUserIdAndInvestDate(Long userId, LocalDate investDate);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "sectors")
    @Query("select i from Investment i where i.userId = :userId and i.investDate = :investDate")
    Optional<Investment> findWithSectorsForUpdate(
            @Param("userId") Long userId, @Param("investDate") LocalDate investDate);

    List<Investment> findByUserIdAndSettlementStatusInAndInvestDateLessThan(
            Long userId, Collection<SettlementStatus> settlementStatuses, LocalDate investDate);

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
