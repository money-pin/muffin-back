-- 이슈 #40 배포 전 적용: 자정 마감 시각 저장
ALTER TABLE investment
    ADD COLUMN finalized_at DATETIME(6) NULL;

-- 기존 투자 데이터는 조회·정산 호환성을 위해 NULL을 유지한다.
-- 롤백이 필요하면 애플리케이션을 이전 버전으로 내린 뒤 아래 DDL을 실행한다.
-- ALTER TABLE investment DROP COLUMN finalized_at;
