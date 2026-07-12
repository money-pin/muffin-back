package com.muffin.sector.domain.etfprice;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** EtfPrice 애그리거트 리포지토리. */
public interface EtfPriceRepository extends JpaRepository<EtfPrice, Long> {

    Optional<EtfPrice> findByEtfIdAndPriceDate(Long etfId, LocalDate priceDate);
}
