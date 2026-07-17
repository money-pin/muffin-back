package com.muffin.sector.domain.etf;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Etf 애그리거트 리포지토리. */
public interface EtfRepository extends JpaRepository<Etf, Long> {

    Optional<Etf> findByEtfCode(String etfCode);

    /** 특정 데이터 제공처가 담당하는 ETF만 조회한다. 수집기가 자신이 조회할 수 없는 종목까지 대상에 포함하지 않도록 한다. */
    List<Etf> findAllByPriceProvider(PriceProvider priceProvider);
}
