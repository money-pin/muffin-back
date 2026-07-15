-- Issue #23 배포 전 운영 DB에 1회 적용한다.
-- application-prod.yml의 ddl-auto=validate가 새 컬럼을 요구하므로 애플리케이션 배포보다 먼저 실행해야 한다.
ALTER TABLE investment
    ADD COLUMN settlement_due_date DATE NULL,
    ADD COLUMN finalized_at DATETIME(6) NULL;

-- 기존 종료 건은 이미 정산 처리가 끝났으므로 마감 완료 시각을 보정한다.
-- PENDING/FAILED 건은 다음 배치가 다시 처리할 수 있도록 NULL을 유지한다.
UPDATE investment
SET finalized_at = COALESCE(settled_at, updated_at)
WHERE finalized_at IS NULL
  AND settlement_status IN ('SETTLED', 'NO_SETTLEMENT', 'CANCELLED');
