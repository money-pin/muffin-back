package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시딩된 모든 ETF의 특정 일자 시세를 토스증권 API에서 가져와 저장한다. ETF 하나가 실패해도 나머지 ETF 수집은 계속 진행한다.
 *
 * <p>시가와 종가는 서로 다른 시각(장 시작 직후 / 장 마감 이후)에 확정되므로 {@link #collectOpen(LocalDate)}와
 * {@link #collectClose(LocalDate)}로 분리했다. 하나의 호출에서 둘 다 기록하면 장중 호출 시 아직 확정되지 않은 종가를
 * 실제 종가처럼 저장할 위험이 있다.
 *
 * <p>해당 일자의 캔들 자체가 없으면(휴장일 등) skip으로 집계하고 성공/실패 어느 쪽으로도 단정하지 않는다. 이 클래스는
 * 거래일 여부를 판단할 방법이 없기 때문이다(거래일 판정은 §5.2, 별도 서비스의 책임).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceCollector {

    private final EtfRepository etfRepository;
    private final TossMarketDataClient tossMarketDataClient;
    private final EtfPriceWriter etfPriceWriter;

    /** 장 시작 이후 호출해 시가를 수집한다. */
    public CollectionSummary collectOpen(LocalDate date) {
        return collect(date, Candle::openPrice, (etfId, price) -> etfPriceWriter.writeOpen(etfId, date, price));
    }

    /** 장 마감 이후 호출해 종가를 수집한다. */
    public CollectionSummary collectClose(LocalDate date) {
        return collect(date, Candle::closePrice, (etfId, price) -> etfPriceWriter.writeClose(etfId, date, price));
    }

    private CollectionSummary collect(
            LocalDate date, Function<Candle, String> priceField, BiConsumer<Long, Long> writer) {
        List<Etf> etfs = etfRepository.findAll();
        int successCount = 0;
        List<String> skippedEtfCodes = new ArrayList<>();
        List<String> failedEtfCodes = new ArrayList<>();

        for (Etf etf : etfs) {
            Optional<Candle> candle;
            try {
                candle = tossMarketDataClient.getDailyCandle(etf.getEtfCode(), date);
            } catch (TossApiException e) {
                log.warn("ETF 시세 조회 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                failedEtfCodes.add(etf.getEtfCode());
                continue;
            }

            if (candle.isEmpty()) {
                skippedEtfCodes.add(etf.getEtfCode());
                continue;
            }

            Optional<Long> price = parsePrice(priceField.apply(candle.get()));
            if (price.isEmpty()) {
                failedEtfCodes.add(etf.getEtfCode());
                continue;
            }

            try {
                writer.accept(etf.getId(), price.get());
                successCount++;
            } catch (RuntimeException e) {
                log.warn("ETF 시세 저장 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                failedEtfCodes.add(etf.getEtfCode());
            }
        }

        return new CollectionSummary(
                successCount,
                skippedEtfCodes.size(),
                failedEtfCodes.size(),
                List.copyOf(skippedEtfCodes),
                List.copyOf(failedEtfCodes));
    }

    private Optional<Long> parsePrice(String rawPrice) {
        if (rawPrice == null) {
            return Optional.empty();
        }
        try {
            long price = new BigDecimal(rawPrice).longValueExact();
            if (price <= 0) {
                log.warn("가격은 0보다 커야 합니다. rawPrice={}", rawPrice);
                return Optional.empty();
            }
            return Optional.of(price);
        } catch (NumberFormatException | ArithmeticException e) {
            log.warn("가격 값을 파싱할 수 없습니다. rawPrice={}", rawPrice);
            return Optional.empty();
        }
    }

    public record CollectionSummary(
            int successCount,
            int skippedCount,
            int failureCount,
            List<String> skippedEtfCodes,
            List<String> failedEtfCodes) {}
}
