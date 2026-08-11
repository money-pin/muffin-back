package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * ETF 시세를 저장한다. 같은 (etfId, priceDate) 레코드가 있으면 갱신하고, 없으면 새로 만든다.
 *
 * <p>같은 (etfId, priceDate)에 대한 변경은 행 잠금이 적용된 독립 트랜잭션에서 수행한다. 신규 행 생성이 경합해
 * {@code uk_etf_price_etf_price_date} 위반이 발생하면 실패한 트랜잭션이 끝난 뒤 새 트랜잭션에서 먼저 커밋된 행을 갱신한다.
 */
@Component
@RequiredArgsConstructor
public class EtfPriceWriter {

    private final EtfPriceWriteTransaction writeTransaction;

    public void writeOpen(Long etfId, LocalDate priceDate, Long startPrice) {
        upsert(
                etfId,
                priceDate,
                existing -> existing.recordOpen(startPrice),
                () -> EtfPrice.open(etfId, priceDate, startPrice));
    }

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

    public void markBaseFailed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPriceWriter::markBothFailed,
                () -> marked(etfId, priceDate, EtfPriceWriter::markBothFailed));
    }

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

    public void markOpenNoData(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markOpenNoData, () -> marked(etfId, priceDate, EtfPrice::markOpenNoData));
    }

    public void markOpenFailed(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markOpenFailed, () -> marked(etfId, priceDate, EtfPrice::markOpenFailed));
    }

    public void markOpenFinalMissing(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markOpenFinalMissing,
                () -> marked(etfId, priceDate, EtfPrice::markOpenFinalMissing));
    }

    public void markOpenMarketClosed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markOpenMarketClosed,
                () -> marked(etfId, priceDate, EtfPrice::markOpenMarketClosed));
    }

    public void markCloseNoData(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markCloseNoData, () -> marked(etfId, priceDate, EtfPrice::markCloseNoData));
    }

    public void markCloseFailed(Long etfId, LocalDate priceDate) {
        upsert(etfId, priceDate, EtfPrice::markCloseFailed, () -> marked(etfId, priceDate, EtfPrice::markCloseFailed));
    }

    public void markCloseFinalMissing(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markCloseFinalMissing,
                () -> marked(etfId, priceDate, EtfPrice::markCloseFinalMissing));
    }

    public void markCloseMarketClosed(Long etfId, LocalDate priceDate) {
        upsert(
                etfId,
                priceDate,
                EtfPrice::markCloseMarketClosed,
                () -> marked(etfId, priceDate, EtfPrice::markCloseMarketClosed));
    }

    void upsert(Long etfId, LocalDate priceDate, Consumer<EtfPrice> update, Supplier<EtfPrice> create) {
        try {
            writeTransaction.insertOrUpdate(etfId, priceDate, update, create);
        } catch (DataIntegrityViolationException exception) {
            if (!writeTransaction.updateExisting(etfId, priceDate, update)) {
                throw exception;
            }
        }
    }

    private EtfPrice marked(Long etfId, LocalDate priceDate, Consumer<EtfPrice> marker) {
        EtfPrice price = EtfPrice.pending(etfId, priceDate);
        marker.accept(price);
        return price;
    }
}
