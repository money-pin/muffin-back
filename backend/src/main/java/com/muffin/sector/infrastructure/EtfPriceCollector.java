package com.muffin.sector.infrastructure;

import com.muffin.global.event.EtfPricesLoadedEvent;
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
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 시딩된 모든 ETF의 특정 일자 시세를 토스증권 API에서 가져와 저장한다. ETF 하나가 실패해도 나머지 ETF 수집은 계속 진행한다.
 *
 * <p>시가와 종가는 서로 다른 시각(장 시작 직후 / 장 마감 이후)에 확정되므로 {@link #collectOpen(LocalDate)}와
 * {@link #collectClose(LocalDate)}로 분리했다. 하나의 호출에서 둘 다 기록하면 장중 호출 시 아직 확정되지 않은 종가를
 * 실제 종가처럼 저장할 위험이 있다.
 *
 * <p>{@link TossMarketDataClient#getMarketCalendar}로 거래일 여부를 먼저 확인해, 거래일이 아니면 API 호출
 * 없이 전체를 MARKET_CLOSED로 기록한다. 거래일인데 특정 ETF만 캔들이 없는 경우는 거래정지인지 데이터 반영 지연인지 이
 * 시점에서 단정할 수 없으므로 NO_DATA로 기록한다. API·파싱·저장 실패는 FAILED로 기록해 가격 null과 실패 원인을 구분한다.
 * 시가가 모두 정상 수집된 경우에만 정산 시작 이벤트를 발행하며, 10:00 FINAL_MISSING 전환과 0% 폴백은 정산 배치의 책임이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceCollector {

    private final EtfRepository etfRepository;
    private final TossMarketDataClient tossMarketDataClient;
    private final EtfPriceWriter etfPriceWriter;
    private final ApplicationEventPublisher eventPublisher;

    /** 장 시작 이후 호출해 시가를 수집한다. */
    public CollectionSummary collectOpen(LocalDate date) {
        CollectionSummary summary = collect(date, Candle::openPrice, CollectionTarget.OPEN);
        if (summary.isFullySuccessful()) {
            eventPublisher.publishEvent(new EtfPricesLoadedEvent(date));
        }
        return summary;
    }

    /** 장 마감 이후 호출해 종가를 수집한다. */
    public CollectionSummary collectClose(LocalDate date) {
        return collect(date, Candle::closePrice, CollectionTarget.CLOSE);
    }

    private CollectionSummary collect(LocalDate date, Function<Candle, String> priceField, CollectionTarget target) {
        List<Etf> etfs = etfRepository.findAll();
        boolean tradingDay;
        try {
            tradingDay = isTradingDay(date);
        } catch (TossApiException e) {
            log.warn("Market calendar lookup failed. date={}, message={}", date, e.getMessage());
            return markAllFailed(target, etfs, date);
        }

        if (!tradingDay) {
            log.info("거래일이 아니라 ETF 시세 수집을 건너뜁니다. date={}", date);
            List<String> skippedEtfCodes = new ArrayList<>();
            List<String> failedEtfCodes = new ArrayList<>();
            for (Etf etf : etfs) {
                try {
                    markMarketClosed(target, etf.getId(), date);
                    skippedEtfCodes.add(etf.getEtfCode());
                } catch (RuntimeException e) {
                    log.warn(
                            "ETF 휴장 상태 저장 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                    failedEtfCodes.add(etf.getEtfCode());
                }
            }
            return new CollectionSummary(
                    0,
                    skippedEtfCodes.size(),
                    failedEtfCodes.size(),
                    List.copyOf(skippedEtfCodes),
                    List.copyOf(failedEtfCodes));
        }

        int successCount = 0;
        List<String> skippedEtfCodes = new ArrayList<>();
        List<String> failedEtfCodes = new ArrayList<>();

        for (Etf etf : etfs) {
            Optional<Candle> candle;
            try {
                candle = tossMarketDataClient.getDailyCandle(etf.getEtfCode(), date);
            } catch (TossApiException e) {
                log.warn("ETF 시세 조회 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                markFailedSafely(target, etf, date);
                failedEtfCodes.add(etf.getEtfCode());
                continue;
            }

            if (candle.isEmpty()) {
                log.warn("거래일인데 ETF 캔들이 없습니다(거래정지 또는 데이터 반영 지연). etfCode={}, date={}", etf.getEtfCode(), date);
                try {
                    markNoData(target, etf.getId(), date);
                    skippedEtfCodes.add(etf.getEtfCode());
                } catch (RuntimeException e) {
                    log.warn(
                            "ETF 미수신 상태 저장 실패. etfCode={}, date={}, message={}",
                            etf.getEtfCode(),
                            date,
                            e.getMessage());
                    failedEtfCodes.add(etf.getEtfCode());
                }
                continue;
            }

            Optional<Long> price = parsePrice(priceField.apply(candle.get()));
            if (price.isEmpty()) {
                markFailedSafely(target, etf, date);
                failedEtfCodes.add(etf.getEtfCode());
                continue;
            }

            try {
                writePrice(target, etf.getId(), date, price.get());
                successCount++;
            } catch (RuntimeException e) {
                log.warn("ETF 시세 저장 실패. etfCode={}, date={}, message={}", etf.getEtfCode(), date, e.getMessage());
                markFailedSafely(target, etf, date);
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

    private CollectionSummary markAllFailed(CollectionTarget target, List<Etf> etfs, LocalDate date) {
        List<String> failedEtfCodes = new ArrayList<>();
        for (Etf etf : etfs) {
            markFailedSafely(target, etf, date);
            failedEtfCodes.add(etf.getEtfCode());
        }
        return new CollectionSummary(0, 0, failedEtfCodes.size(), List.of(), List.copyOf(failedEtfCodes));
    }

    private void writePrice(CollectionTarget target, Long etfId, LocalDate date, Long price) {
        if (target == CollectionTarget.OPEN) {
            etfPriceWriter.writeOpen(etfId, date, price);
        } else {
            etfPriceWriter.writeClose(etfId, date, price);
        }
    }

    private void markNoData(CollectionTarget target, Long etfId, LocalDate date) {
        if (target == CollectionTarget.OPEN) {
            etfPriceWriter.markOpenNoData(etfId, date);
        } else {
            etfPriceWriter.markCloseNoData(etfId, date);
        }
    }

    private void markFailed(CollectionTarget target, Long etfId, LocalDate date) {
        if (target == CollectionTarget.OPEN) {
            etfPriceWriter.markOpenFailed(etfId, date);
        } else {
            etfPriceWriter.markCloseFailed(etfId, date);
        }
    }

    private void markMarketClosed(CollectionTarget target, Long etfId, LocalDate date) {
        if (target == CollectionTarget.OPEN) {
            etfPriceWriter.markOpenMarketClosed(etfId, date);
        } else {
            etfPriceWriter.markCloseMarketClosed(etfId, date);
        }
    }

    private void markFailedSafely(CollectionTarget target, Etf etf, LocalDate date) {
        try {
            markFailed(target, etf.getId(), date);
        } catch (RuntimeException statusSaveException) {
            log.warn(
                    "ETF 실패 상태 저장 실패. etfCode={}, date={}, message={}",
                    etf.getEtfCode(),
                    date,
                    statusSaveException.getMessage());
        }
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

    private enum CollectionTarget {
        OPEN,
        CLOSE
    }

    public record CollectionSummary(
            int successCount,
            int skippedCount,
            int failureCount,
            List<String> skippedEtfCodes,
            List<String> failedEtfCodes) {

        public boolean isFullySuccessful() {
            return successCount > 0 && skippedCount == 0 && failureCount == 0;
        }
    }
}
