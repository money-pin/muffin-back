package com.muffin.investment.domain.investment;

import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
