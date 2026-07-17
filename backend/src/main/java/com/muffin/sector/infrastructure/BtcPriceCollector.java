package com.muffin.sector.infrastructure;

import com.muffin.global.event.EtfPricesLoadedEvent;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.infrastructure.coingecko.CoinGeckoClient;
import com.muffin.sector.infrastructure.coingecko.exception.CoinGeckoApiException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 코인 섹터(BTC)의 일별 기준가를 CoinGecko API에서 가져와 저장한다.
 *
 * <p>코인은 24시간 거래되어 시가·종가 구분이 없으므로(§코인 섹터 기준가 정책), 서비스 기준 시각(09:00 KST)에 조회한 단일
 * 가격을 그날의 기준가로 삼아 {@link EtfPriceWriter#writeBasePrice}로 시가·종가에 동일하게 기록한다. 다른 11개
 * ETF와 동일하게 {@link TradingCalendarService}로 거래일에만 수집하고, 거래일이 아니면 API 호출 없이
 * MARKET_CLOSED로 기록한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BtcPriceCollector {

    private static final String BTC_ETF_CODE = "BTC";

    private final EtfRepository etfRepository;
    private final TradingCalendarService tradingCalendarService;
    private final CoinGeckoClient coinGeckoClient;
    private final EtfPriceWriter etfPriceWriter;
    private final ApplicationEventPublisher eventPublisher;

    public void collect(LocalDate date) {
        boolean tradingDay = tradingCalendarService.getCalendar(date).tradingDay();
        Etf btc = etfRepository.findByEtfCode(BTC_ETF_CODE).orElse(null);
        if (btc == null) {
            log.warn("BTC ETF 기준 데이터가 없어 코인 시세 수집을 건너뜁니다.");
            return;
        }

        if (!tradingDay) {
            log.info("거래일이 아니라 코인 시세 수집을 건너뜁니다. date={}", date);
            etfPriceWriter.markBaseMarketClosed(btc.getId(), date);
            return;
        }

        Long price;
        try {
            price = coinGeckoClient.getBitcoinPriceKrw();
        } catch (CoinGeckoApiException e) {
            log.warn("코인 시세 조회 실패. date={}, message={}", date, e.getMessage());
            markFailedSafely(btc.getId(), date);
            return;
        }

        try {
            etfPriceWriter.writeBasePrice(btc.getId(), date, price);
            eventPublisher.publishEvent(new EtfPricesLoadedEvent(date));
        } catch (RuntimeException e) {
            log.warn("코인 시세 저장 실패. date={}, message={}", date, e.getMessage());
            markFailedSafely(btc.getId(), date);
        }
    }

    private void markFailedSafely(Long etfId, LocalDate date) {
        try {
            etfPriceWriter.markBaseFailed(etfId, date);
        } catch (RuntimeException statusSaveException) {
            log.warn("코인 실패 상태 저장 실패. date={}, message={}", date, statusSaveException.getMessage());
        }
    }
}
