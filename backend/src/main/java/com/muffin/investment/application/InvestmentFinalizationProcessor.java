package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 한 명의 자정 마감을 독립 트랜잭션으로 처리해 다른 사용자 실패와 격리한다. */
@Service
@RequiredArgsConstructor
public class InvestmentFinalizationProcessor {

    private final UserAssetRepository userAssetRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional
    public void finalizeUser(
            Long userAssetId,
            LocalDate investDate,
            LocalDateTime finalizedAt,
            Map<Long, BigDecimal> closePricesBySectorId) {
        UserAsset asset = userAssetRepository.findByIdForUpdate(userAssetId).orElseThrow();
        Investment investment = investmentRepository
                .findWithSectorsForUpdate(asset.getUserId(), investDate)
                .orElseGet(() -> createNoInvest(asset, investDate));

        if (investment.getStatus() == InvestmentStatus.NO_INVEST) {
            investment.finalizeNoInvest(finalizedAt);
            return;
        }
        if (investment.getFinalizedAt() != null && !investment.hasMissingBuyPrice()) {
            return;
        }
        investment.finalizeInvestment(closePricesBySectorId, finalizedAt);
    }

    private Investment createNoInvest(UserAsset asset, LocalDate investDate) {
        Investment noInvest = Investment.noInvest(asset.getUserId(), asset.getId(), investDate);
        return investmentRepository.save(noInvest);
    }
}
