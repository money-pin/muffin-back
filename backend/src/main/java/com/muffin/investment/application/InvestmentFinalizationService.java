package com.muffin.investment.application;

import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 자정 마감 오케스트레이터. 거래일 여부만 확인한 뒤 사용자별 독립 트랜잭션으로 투자 '동결'을 위임한다.
 *
 * <p>매수가/매도가 가격 스냅샷은 정산 배치 phase 1이 소유하므로 여기서는 EtfPrice를 읽지 않는다. 마감은 이후 PATCH를 막는 컷오프 역할만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentFinalizationService {

    private static final int FINALIZATION_CHUNK_SIZE = 500;

    private final TradingCalendarService tradingCalendarService;
    private final UserAssetRepository userAssetRepository;
    private final InvestmentFinalizationProcessor processor;

    public InvestmentFinalizationResult finalizeInvestments(LocalDate investDate, LocalDateTime finalizedAt) {
        if (!tradingCalendarService.getCalendar(investDate).tradingDay()) {
            return InvestmentFinalizationResult.marketClosed(investDate);
        }

        LocalDateTime cutoff = investDate.plusDays(1).atStartOfDay();

        int targetCount = 0;
        int success = 0;
        int failure = 0;
        Pageable pageable =
                PageRequest.of(0, FINALIZATION_CHUNK_SIZE, Sort.by("id").ascending());
        Slice<UserAsset> targets;
        do {
            targets = userAssetRepository.findByCreatedAtBefore(cutoff, pageable);
            targetCount += targets.getNumberOfElements();
            for (UserAsset target : targets.getContent()) {
                try {
                    processor.finalizeUser(target.getId(), investDate, finalizedAt);
                    success++;
                } catch (RuntimeException exception) {
                    failure++;
                    log.error(
                            "[investment-finalization] failed userAssetId={} investDate={}",
                            target.getId(),
                            investDate,
                            exception);
                }
            }
            pageable = targets.nextPageable();
        } while (targets.hasNext());

        InvestmentFinalizationResult result =
                new InvestmentFinalizationResult(investDate, true, targetCount, success, failure);
        return result;
    }
}
