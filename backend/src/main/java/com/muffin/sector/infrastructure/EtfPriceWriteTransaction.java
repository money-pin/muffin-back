package com.muffin.sector.infrastructure;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** ETF 시세 한 건의 생성 또는 갱신을 독립된 트랜잭션에서 처리한다. */
@Component
@RequiredArgsConstructor
class EtfPriceWriteTransaction {

    private final EtfPriceRepository etfPriceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertOrUpdate(Long etfId, LocalDate priceDate, Consumer<EtfPrice> update, Supplier<EtfPrice> create) {
        Optional<EtfPrice> existing = etfPriceRepository.findByEtfIdAndPriceDateForUpdate(etfId, priceDate);
        if (existing.isPresent()) {
            update.accept(existing.get());
            return;
        }
        etfPriceRepository.saveAndFlush(create.get());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean updateExisting(Long etfId, LocalDate priceDate, Consumer<EtfPrice> update) {
        Optional<EtfPrice> existing = etfPriceRepository.findByEtfIdAndPriceDateForUpdate(etfId, priceDate);
        if (existing.isEmpty()) {
            return false;
        }
        update.accept(existing.get());
        return true;
    }
}
