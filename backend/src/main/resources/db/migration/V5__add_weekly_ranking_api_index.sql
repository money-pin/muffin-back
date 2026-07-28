-- V4(#105) 주간 랭킹 집계 배치 이후, 지난주 TOP 10 조회를 위한 인덱스
CREATE INDEX idx_weekly_ranking_week_position
    ON weekly_ranking (week_start_date, ranking_position);
