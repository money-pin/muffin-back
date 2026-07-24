package com.muffin.sector.infrastructure;

import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossCandleResponse.Candle;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 시딩된 ETF의 특정 일자 시세를 토스증권 API에서 가져와 저장한다. ETF 하나가 실패해도 나머지 ETF 수집은 계속 진행한다.
 *
 * <p>{@link #NON_TOSS_ETF_CODES}에 해당하는 종목(BTC 등 CoinGecko로 수집하는 코인 섹터)은 토스 API로 조회할 수
 * 없으므로 이 수집기의 대상에서 제외한다({@link BtcPriceCollector}가 별도로 담당).
 *
 * <p>시가와 종가는 서로 다른 시각(장 시작 직후 / 장 마감 이후)에 확정되므로 {@link #collectOpen(LocalDate)}와
 * {@link #collectClose(LocalDate)}로 분리했다. 하나의 호출에서 둘 다 기록하면 장중 호출 시 아직 확정되지 않은 종가를
 * 실제 종가처럼 저장할 위험이 있다.
 *
 * <p>{@link TradingCalendarService}로 거래일 여부를 먼저 확인해, 거래일이 아니면 API 호출 없이 전체를
 * MARKET_CLOSED로 기록한다. 거래일인데 특정 ETF만 캔들이 없는 경우는 거래정지인지 데이터 반영 지연인지 이 시점에서 단정할 수 없으므로
 * NO_DATA로 기록한다. API·파싱·저장 실패는 FAILED로 기록해 가격 null과 실패 원인을 구분한다.
 * 완료 이벤트 발행과 09:30 시가 FINAL_MISSING 전환은 통합 시가 수집 오케스트레이터가 담당한다. 종가는 16:05 마지막 수집 후
 * {@link #finalizeMissingClosePrices(LocalDate)}가 같은 상태로 종결한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EtfPriceCollector {

    /** 토스증권이 아닌 다른 데이터 제공처로 수집하는 ETF 코드. {@code BtcPriceCollector}의 대상 코드와 맞춰 둔다. */
    private static final Set<String> NON_TOSS_ETF_CODES = Set.of("BTC");

    private final EtfRepository etfRepository;
    private final TradingCalendarService tradingCalendarService;
    private final TossMarketDataClient tossMarketDataClient;
    private final EtfPriceWriter etfPriceWriter;
    // TODO : 확인필요 - 이슈 #40 종가 재시도에서 이미 성공한 종목의 외부 API 재호출을 피하기 위해 기존 수집기에 상태 조회를 추가함.
    private final EtfPriceRepository etfPriceRepository;

    /** 장 시작 이후 호출해 시가를 수집한다. */
    public CollectionSummary collectOpen(LocalDate date) {
        return collectOpen(date, tradingCalendarService.getCalendar(date).tradingDay());
    }

    public CollectionSummary collectOpen(LocalDate date, boolean tradingDay) {
        return collect(date, Candle::openPrice, CollectionTarget.OPEN, tradingDay);
    }

    /** 장 마감 이후 호출해 종가를 수집한다. */
    public CollectionSummary collectClose(LocalDate date) {
        return collect(
                date,
                Candle::closePrice,
                CollectionTarget.CLOSE,
                tradingCalendarService.getCalendar(date).tradingDay());
    }

    /** 16:05 마지막 종가 수집 뒤, 미확보 종가를 FINAL_MISSING으로 종결한다. */
    public void finalizeMissingClosePrices(LocalDate date) {
        Map<Long, EtfPrice> pricesByEtfId = etfPriceRepository.findByPriceDate(date).stream()
                .collect(Collectors.toMap(EtfPrice::getEtfId, Function.identity(), (left, right) -> left));

        for (Etf etf : tossEtfs()) {
            if (isCloseTerminal(pricesByEtfId.get(etf.getId()))) {
                continue;
            }
            try {
                etfPriceWriter.markCloseFinalMissing(etf.getId(), date);
            } catch (RuntimeException exception) {
                log.error("ETF 종가 FINAL_MISSING 저장 실패. etfCode={}, date={}", etf.getEtfCode(), date, exception);
            }
        }
    }

    private CollectionSummary collect(
            LocalDate date, Function<Candle, String> priceField, CollectionTarget target, boolean tradingDay) {
        List<Etf> etfs = tossEtfs();

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
            if (alreadyCollected(target, etf.getId(), date)) {
                successCount++;
                continue;
            }
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

    private boolean alreadyCollected(CollectionTarget target, Long etfId, LocalDate date) {
        return etfPriceRepository
                .findByEtfIdAndPriceDate(etfId, date)
                .map(price -> target == CollectionTarget.OPEN
                        ? price.getStartPriceStatus() == PriceCollectionStatus.SUCCESS
                        : price.getEndPriceStatus() == PriceCollectionStatus.SUCCESS)
                .orElse(false);
    }

    private List<Etf> tossEtfs() {
        return etfRepository.findAll().stream()
                .filter(etf -> !NON_TOSS_ETF_CODES.contains(etf.getEtfCode()))
                .toList();
    }

    private boolean isCloseTerminal(EtfPrice price) {
        if (price == null) {
            return false;
        }
        PriceCollectionStatus status = price.getEndPriceStatus();
        return status == PriceCollectionStatus.SUCCESS
                || status == PriceCollectionStatus.FINAL_MISSING
                || status == PriceCollectionStatus.MARKET_CLOSED;
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
            List<String> failedEtfCodes) {}
}
