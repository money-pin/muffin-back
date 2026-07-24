-- profit_summary 폐기 (#85)
-- 어떤 API 응답에도 쓰이지 않는 write-only 잉여 테이블. 누적/일별 손익은 stats가 investment_sector에서 직접 집계하므로 불필요.
DROP TABLE IF EXISTS `profit_summary`;
