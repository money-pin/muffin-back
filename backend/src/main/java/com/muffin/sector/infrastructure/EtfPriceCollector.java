package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse;
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
 * <p>{@link TossMarketDataClient#getMarketCalendar}로 거래일 여부를 먼저 확인한다. 거래일이 아니면 전체를
 * skip하고, 거래일인데 특정 ETF만 캔들이 없으면 거래정지로 간주해 시세 0을 명시적으로 저장한다. 이렇게 저장된 행은
 * 정산 쪽의 "적재 완료 가드"는 통과하되 "가격 유효성 검사"에서는 걸러져 0% 폴백으로 처리된다. 행 자체가 없는 경우(=이번
 * skip)는 아직 수집을 시도하지 않았다는 뜻으로 남아 정산 전체가 보류된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceCollector {

    private static final long HALTED_PRICE = 0L;

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
        if (!isTradingDay(date)) {
            log.info("거래일이 아니라 ETF 시세 수집을 건너뜁니다. date={}", date);
            List<String> etfCodes =
                    etfRepository.findAll().stream().map(Etf::getEtfCode).toList();
            return new CollectionSummary(0, etfCodes.size(), 0, etfCodes, List.of());
        }

        List<Etf> etfs = etfRepository.findAll();
        int successCount = 0;
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

            Optional<Long> price;
            if (candle.isPresent()) {
                price = parsePrice(priceField.apply(candle.get()));
            } else {
                log.info("거래일인데 캔들이 없어 거래정지로 판단해 0으로 기록합니다. etfCode={}, date={}", etf.getEtfCode(), date);
                price = Optional.of(HALTED_PRICE);
            }

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

        return new CollectionSummary(successCount, 0, failedEtfCodes.size(), List.of(), List.copyOf(failedEtfCodes));
    }

    private boolean isTradingDay(LocalDate date) {
        TossMarketCalendarResponse.Result calendar = tossMarketDataClient.getMarketCalendar(date);
        return calendar.today() != null
                && calendar.today().integrated() != null
                && calendar.today().integrated().regularMarket() != null;
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
