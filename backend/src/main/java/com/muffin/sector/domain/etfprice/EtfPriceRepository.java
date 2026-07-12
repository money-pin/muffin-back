package com.muffin.sector.domain.etfprice;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** EtfPrice 애그리거트 리포지토리. */
public interface EtfPriceRepository extends JpaRepository<EtfPrice, Long> {

    /** 특정 일자의 전체 ETF 시세. 정산 배치가 etfId→시세 맵을 만드는 용도. */
    List<EtfPrice> findByPriceDate(LocalDate priceDate);

    /** 특정 일자 시세가 하나라도 적재됐는지. 정산 스케줄러의 적재 완료 가드에 사용한다. */
    boolean existsByPriceDate(LocalDate priceDate);

    /** 기준일 이전의 가장 최근 시세 일자(= 직전 거래일). 정산 대상의 due/stale 판정에 사용한다. 없으면 null. */
    @Query("select max(e.priceDate) from EtfPrice e where e.priceDate < :date")
    LocalDate findLatestPriceDateBefore(@Param("date") LocalDate date);
}
