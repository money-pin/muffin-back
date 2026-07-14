package com.muffin.investment.application.projection;

import com.muffin.investment.domain.investment.enums.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정산 결과 조회용 읽기 전용 projection.
 */
public record SettlementResultProjection(
        SettlementStatus settlementStatus,
        LocalDate investDate,
        Long totalAmount,
        Long totalProfitLoss,
        BigDecimal totalProfitLossRate,
        Long totalAsset) {}
