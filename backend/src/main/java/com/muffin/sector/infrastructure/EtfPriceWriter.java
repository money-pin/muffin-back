package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * ETF 시세를 저장한다. 같은 (etfId, priceDate) 레코드가 있으면 갱신하고, 없으면 새로 만든다.
 *
 * <p>조회 후 저장하는 구조라 같은 (etfId, priceDate)에 대한 동시 호출은 경쟁할 수 있다. 새로 만들다가
 * {@code uk_etf_price_etf_price_date} 위반이 발생하면, 먼저 커밋된 행을 다시 조회해 그 위에 갱신한다.
 */
@Component
@RequiredArgsConstructor
public class EtfPriceWriter {

    private final EtfPriceRepository etfPriceRepository;

    @Transactional
    public void writeOpen(Long etfId, LocalDate priceDate, Long startPrice) {
        upsert(
                etfId,
                priceDate,
                existing -> existing.recordOpen(startPrice),
                () -> EtfPrice.open(etfId, priceDate, startPrice));
    }

    @Transactional
    public void writeClose(Long etfId, LocalDate priceDate, Long endPrice) {
        upsert(
                etfId,
                priceDate,
                existing -> existing.recordClose(endPrice),
                () -> EtfPrice.create(etfId, priceDate, null, endPrice));
    }

    /**
     * 코인 섹터 기준가를 기록한다. 코인은 24시간 거래되어 시가·종가 구분이 없으므로(§코인 섹터 기준가 정책), 서비스 기준
     * 시각(09:00 KST)의 단일 가격을 시가·종가 두 필드에 동일하게 반영해 기존 정산 계산식을 그대로 재사용한다.
     *
     * <p>토스 ETF의 {@code writeOpen}/{@code writeClose}와 달리, 이미 {@code SUCCESS}로 기록된 기준가는 재호출해도
     * 덮어쓰지 않는다. 토스 ETF 시가는 최종 확정 전 재조회 결과로 갱신될 수 있다. 반면 CoinGecko
     * `simple/price`는 호출 시점의 실시간가라 같은 날 다시 호출하면 다른 값이 온다. 09:00 기준가가 이후 재실행(다중
     * 인스턴스 동시 트리거 등)으로 바뀌는 것을 막기 위해, `FAILED` 등 미확정 상태일 때만 갱신을 허용한다.
     */
    @Transactional
    public void writeBasePrice(Long etfId, LocalDate priceDate, Long price) {
        upsert(
                etfId,
                priceDate,
                existing -> writeBasePriceIfNotAlreadySucceeded(existing, price),
                () -> EtfPrice.create(etfId, priceDate, price, price));
    }

    private static void writeBasePriceIfNotAlreadySucceeded(EtfPrice existing, Long price) {
        if (existing.getStartPriceStatus() == PriceCollectionStatus.SUCCESS) {
            return;
        }
        existing.recordOpen(price);
        existing.recordClose(price);
    }

    @Transactional
    public void markBaseFailed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPriceWriter::markBothFailed,
                () -> marked(etfId, priceDate, EtfPriceWriter::markBothFailed));
    }

    @Transactional
    public void markBaseMarketClosed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPriceWriter::markBothMarketClosed,
                () -> marked(etfId, priceDate, EtfPriceWriter::markBothMarketClosed));
    }

    private static void markBothFailed(EtfPrice price) {
        price.markOpenFailed();
        price.markCloseFailed();
    }

    private static void markBothMarketClosed(EtfPrice price) {
        price.markOpenMarketClosed();
        price.markCloseMarketClosed();
    }

    @Transactional
    public void markOpenNoData(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markOpenNoData, () -> marked(etfId, priceDate, EtfPrice::markOpenNoData));
    }

    @Transactional
    public void markOpenFailed(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markOpenFailed, () -> marked(etfId, priceDate, EtfPrice::markOpenFailed));
    }

    @Transactional
    public void markOpenFinalMissing(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markOpenFinalMissing,
                () -> marked(etfId, priceDate, EtfPrice::markOpenFinalMissing));
    }

    @Transactional
    public void markOpenMarketClosed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markOpenMarketClosed,
                () -> marked(etfId, priceDate, EtfPrice::markOpenMarketClosed));
    }

    @Transactional
    public void markCloseNoData(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markCloseNoData, () -> marked(etfId, priceDate, EtfPrice::markCloseNoData));
    }

    @Transactional
    public void markCloseFailed(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markCloseFailed, () -> marked(etfId, priceDate, EtfPrice::markCloseFailed));
    }

    @Transactional
    public void markCloseFinalMissing(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markCloseFinalMissing,
                () -> marked(etfId, priceDate, EtfPrice::markCloseFinalMissing));
    }

    @Transactional
    public void markCloseMarketClosed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markCloseMarketClosed,
                () -> marked(etfId, priceDate, EtfPrice::markCloseMarketClosed));
    }

    private void upsert(Long etfId, LocalDate priceDate, Consumer<EtfPrice> update, Supplier<EtfPrice> create) {
        Optional<EtfPrice> existing = etfPriceRepository.findByEtfIdAndPriceDate(etfId, priceDate);
        if (existing.isPresent()) {
            update.accept(existing.get());
            return;
        }

        try {
            etfPriceRepository.saveAndFlush(create.get());
        } catch (DataIntegrityViolationException e) {
            EtfPrice raceWinner =
                    etfPriceRepository.findByEtfIdAndPriceDate(etfId, priceDate).orElseThrow(() -> e);
            update.accept(raceWinner);
        }
    }

    private EtfPrice marked(Long etfId, LocalDate priceDate, Consumer<EtfPrice> marker) {
        EtfPrice price = EtfPrice.pending(etfId, priceDate);
        marker.accept(price);
        return price;
    }
}
