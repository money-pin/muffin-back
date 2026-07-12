package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** ETF 시세를 저장한다. 같은 (etfId, priceDate) 레코드가 있으면 갱신하고, 없으면 새로 만든다. */
@Component
@RequiredArgsConstructor
public class EtfPriceWriter {

    private final EtfPriceRepository etfPriceRepository;

    @Transactional
    public void writeOpen(Long etfId, LocalDate priceDate, Long startPrice) {
        Optional<EtfPrice> existing = etfPriceRepository.findByEtfIdAndPriceDate(etfId, priceDate);
        if (existing.isPresent()) {
            existing.get().recordOpen(startPrice);
        } else {
            etfPriceRepository.save(EtfPrice.open(etfId, priceDate, startPrice));
        }
    }

    @Transactional
    public void writeClose(Long etfId, LocalDate priceDate, Long endPrice) {
        Optional<EtfPrice> existing = etfPriceRepository.findByEtfIdAndPriceDate(etfId, priceDate);
        if (existing.isPresent()) {
            existing.get().recordClose(endPrice);
        } else {
            etfPriceRepository.save(EtfPrice.create(etfId, priceDate, null, endPrice));
        }
    }
}
