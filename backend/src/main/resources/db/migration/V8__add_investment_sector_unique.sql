-- investment_sector에 (investment_id, sector_id) 유니크 추가
--
-- 한 투자에 같은 섹터가 두 번 들어가면 총 투자금 합계와 섹터별 손익 통계가 모두 어긋난다.
-- 지금은 애그리거트 루트의 배분 병합 로직만 이를 막고 있어, 저장 경로가 하나 늘어나면 조용히 깨진다.
--
-- 적용 전 운영 데이터에서 확인함: 중복 (investment_id, sector_id) 조합 0건.
--
-- 컬럼 삭제(V9)와 파일을 나눈 이유: MySQL은 DDL마다 암묵적 커밋이 일어나 한 파일에 두 ALTER를 넣으면
-- 뒤 문장이 실패해도 앞 문장은 이미 적용된 채 남는다. 그런데 Flyway는 그 버전을 실패로 기록하므로
-- 재시도가 앞 문장에서 다시 막히고 수동 repair가 필요해진다. 파일을 나누면 각각 독립적으로 추적된다.
ALTER TABLE investment_sector
    ADD CONSTRAINT uk_investment_sector_investment_sector UNIQUE (investment_id, sector_id);
