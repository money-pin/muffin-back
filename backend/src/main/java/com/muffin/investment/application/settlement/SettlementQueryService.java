package com.muffin.investment.application.settlement;

import com.muffin.investment.application.projection.SettlementResultProjection;
import com.muffin.investment.presentation.dto.SettlementReason;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 결과 팝업 조회(INVEST-06) 읽기 서비스. 최근 정산 대상 투자를 조회해 상태별로 응답을 만든다.
 */
@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final SettlementQueryRepository settlementQueryRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SettlementResultResponse getRecentSettlementResult(Long userId) {
        LocalDate today = LocalDate.now(clock);
        return settlementQueryRepository
                .findRecentDueSettlement(userId, today)
                .map(this::toResponse)
                .orElseGet(() -> SettlementResultResponse.reason(SettlementReason.NO_INVESTMENT));
    }

    private SettlementResultResponse toResponse(SettlementResultProjection projection) {
        return switch (projection.settlementStatus()) {
            case SETTLED ->
                SettlementResultResponse.settled(
                        projection.investDate(),
                        projection.totalProfitLoss(),
                        projection.totalProfitLossRate(),
                        projection.totalAmount(),
                        projection.totalAsset());
            case PENDING, FAILED -> SettlementResultResponse.reason(SettlementReason.SETTLEMENT_PENDING);
            case CANCELLED, NO_SETTLEMENT -> SettlementResultResponse.reason(SettlementReason.NO_INVESTMENT);
        };
    }
}
