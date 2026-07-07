package com.muffin.sector.domain.etfprice;

import org.springframework.data.jpa.repository.JpaRepository;

/** EtfPrice 애그리거트 리포지토리. */
public interface EtfPriceRepository extends JpaRepository<EtfPrice, Long> {}
