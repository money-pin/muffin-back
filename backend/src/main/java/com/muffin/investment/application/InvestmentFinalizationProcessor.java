package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 한 명의 자정 마감을 독립 트랜잭션으로 처리해 다른 사용자 실패와 격리한다.
 *
 * <p>마감은 투자 구성을 '동결'하는 것(이후 PATCH 차단)만 담당한다. 매수가/매도가 스냅샷은 정산 배치 phase 1의 책임이라 여기서는 EtfPrice를 읽지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InvestmentFinalizationProcessor {

    private final UserAssetRepository userAssetRepository;
    private final InvestmentRepository investmentRepository;

    @Transactional
    public void finalizeUser(Long userAssetId, LocalDate investDate, LocalDateTime finalizedAt) {
        UserAsset asset = userAssetRepository.findByIdForUpdate(userAssetId).orElseThrow();
        Investment investment = investmentRepository
                .findWithSectorsForUpdate(asset.getUserId(), investDate)
                .orElseGet(() -> createNoInvest(asset, investDate));

        if (investment.getStatus() == InvestmentStatus.NO_INVEST) {
            investment.finalizeNoInvest(finalizedAt);
            return;
        }
        if (investment.getFinalizedAt() != null) {
            return; // 이미 동결됨(멱등).
        }
        investment.finalizeInvestment(finalizedAt);
    }

    private Investment createNoInvest(UserAsset asset, LocalDate investDate) {
        Investment noInvest = Investment.noInvest(asset.getUserId(), investDate);
        return investmentRepository.save(noInvest);
    }
}
