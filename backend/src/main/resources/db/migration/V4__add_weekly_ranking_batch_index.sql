-- 지난주 정산 완료 투자 및 미정산 투자 확인을 위한 랭킹 배치 전용 인덱스
CREATE INDEX idx_investment_weekly_ranking
    ON investment (status, settlement_status, invest_date, user_id);
