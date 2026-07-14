package com.muffin.sector.domain.etfprice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** EtfPrice 애그리거트 리포지토리. */
public interface EtfPriceRepository extends JpaRepository<EtfPrice, Long> {

    /** 특정 일자의 전체 ETF 시세. 정산 배치가 적재 완료 가드와 etfId→시세 맵 구성에 사용한다. */
    List<EtfPrice> findByPriceDate(LocalDate priceDate);

    /** 기준일 이전에 정상 종가가 하나라도 수집된 가장 최근 일자(= 직전 거래일). 휴장 상태 행은 제외한다. */
    @Query("select max(e.priceDate) from EtfPrice e where e.priceDate < :date and e.endPrice is not null")
    LocalDate findLatestPriceDateBefore(@Param("date") LocalDate date);

    Optional<EtfPrice> findByEtfIdAndPriceDate(Long etfId, LocalDate priceDate);
}
