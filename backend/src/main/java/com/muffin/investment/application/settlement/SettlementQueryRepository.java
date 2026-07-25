package com.muffin.investment.application.settlement;

import com.muffin.investment.application.projection.SettlementResultProjection;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 정산 결과 조회 포트. 읽기 서비스가 의존하는 인터페이스.
 */
public interface SettlementQueryRepository {

    /** 정산일이 지난 가장 최근 투자와 총자산을 조회한다. */
    Optional<SettlementResultProjection> findRecentDueSettlement(Long userId, LocalDate today);
}
