package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
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
