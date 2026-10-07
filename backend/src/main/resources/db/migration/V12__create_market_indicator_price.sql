-- 모닝 브리핑 "간밤의 시장" 지표. (SYS-08)
-- 코스피·코스닥은 토스 지표 엔드포인트, 해외 지수는 추종 ETF(SPY·QQQ) 종가, 원/달러는 환율 엔드포인트로 받는다.
-- 등락률을 저장해 두는 이유는 섹터 성적표와 같다. 조회 시점에 다시 계산하면 직전 거래일 판정이 달라질 때
-- 과거 브리핑의 숫자가 바뀐다.
-- 상태 컬럼은 enum이 아니라 varchar로 둔다. 상태가 늘 때 ALTER ... MODIFY 없이 값만 추가하면 되고,
-- 엔티티의 @Column(length = 20)과도 정확히 일치한다.

CREATE TABLE `market_indicator_price` (
  `market_indicator_price_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `indicator` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price_date` date NOT NULL,
  -- 지수는 포인트, 환율은 원, 해외는 ETF 달러 가격. 단위는 애플리케이션의 MarketIndicator가 정한다.
  `close_price` decimal(18,4) DEFAULT NULL,
  `change_rate` decimal(9,2) DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`market_indicator_price_id`),
  -- 하루에 여러 번 수집해도 같은 행을 갱신하게 하는 멱등성 키.
  UNIQUE KEY `uk_market_indicator_price_indicator_date` (`indicator`,`price_date`),
  -- 브리핑 조회가 지표별로 기준일 이하의 최신 건을 찾는다.
  KEY `idx_market_indicator_price_date` (`price_date`,`indicator`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
