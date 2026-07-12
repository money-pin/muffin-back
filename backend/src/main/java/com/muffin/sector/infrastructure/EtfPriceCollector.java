package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 시딩된 모든 ETF의 특정 일자 시세를 토스증권 API에서 가져와 저장한다. ETF 하나가 실패해도 나머지 ETF 수집은 계속 진행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceCollector {

    private final EtfRepository etfRepository;
    private final TossMarketDataClient tossMarketDataClient;
    private final EtfPriceWriter etfPriceWriter;

    public CollectionSummary collect(LocalDate date) {
        List<Etf> etfs = etfRepository.findAll();
        int successCount = 0;
        List<String> failedEtfCodes = new ArrayList<>();

        for (Etf etf : etfs) {
            try {
                collectOne(etf, date);
                successCount++;
            } catch (TossApiException e) {
                log.warn("ETF 시세 수집 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                failedEtfCodes.add(etf.getEtfCode());
            }
        }

        return new CollectionSummary(successCount, failedEtfCodes.size(), List.copyOf(failedEtfCodes));
    }

    private void collectOne(Etf etf, LocalDate date) {
        TossCandleResponse candle = tossMarketDataClient.getDailyCandle(etf.getEtfCode(), date);
        if (candle.openPrice() != null) {
            etfPriceWriter.writeOpen(etf.getId(), date, candle.openPrice());
        }
        if (candle.closePrice() != null) {
            etfPriceWriter.writeClose(etf.getId(), date, candle.closePrice());
        }
    }

    public record CollectionSummary(int successCount, int failureCount, List<String> failedEtfCodes) {}
}
