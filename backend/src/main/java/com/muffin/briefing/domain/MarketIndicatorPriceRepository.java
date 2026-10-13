package com.muffin.briefing.domain;

import com.muffin.briefing.domain.enums.MarketIndicator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketIndicatorPriceRepository extends JpaRepository<MarketIndicatorPrice, Long> {

    Optional<MarketIndicatorPrice> findByIndicatorAndPriceDate(MarketIndicator indicator, LocalDate priceDate);

    /** 브리핑이 표시할 지표. 기준일 이하에서 지표별로 가장 최근 정상 수신 건을 쓴다. */
    @Query(
            """
            SELECT p
            FROM MarketIndicatorPrice p
            WHERE p.priceDate <= :baseDate
              AND p.status = com.muffin.briefing.domain.MarketIndicatorStatus.SUCCESS
              AND p.closePrice IS NOT NULL
              AND p.priceDate = (
                  SELECT MAX(latest.priceDate)
                  FROM MarketIndicatorPrice latest
                  WHERE latest.indicator = p.indicator
                    AND latest.priceDate <= :baseDate
                    AND latest.status = com.muffin.briefing.domain.MarketIndicatorStatus.SUCCESS
                    AND latest.closePrice IS NOT NULL
              )
            """)
    List<MarketIndicatorPrice> findLatestUsableOn(@Param("baseDate") LocalDate baseDate);
}
