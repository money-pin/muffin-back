-- 1) investment.user_asset_id 제거
--
-- user_asset은 회원당 1행(uk_user_asset_user)이라 user_asset_id는 user_id로 결정되는 값이었다.
-- 같은 사실을 두 컬럼에 나눠 담은 이행적 종속이라 제거한다.
-- 이 컬럼을 읽던 곳은 두 군데(정산 결과 팝업 조회, 정산 자산 반영)이고, 둘 다 user_id 조회로 바꿨다.
-- uk_investment_user_invest_date와 idx_investment_weekly_ranking은 user_id만 쓰므로 영향이 없다.
--
-- 적용 전 운영 데이터에서 확인함:
--   - investment JOIN user_asset 시 user_id 불일치 0건
--   - user_asset_id가 가리키는 행이 없는 고아 0건
-- 즉 이 컬럼을 지워도 잃는 정보가 없다.
ALTER TABLE investment DROP COLUMN user_asset_id;

-- 2) investment_sector에 (investment_id, sector_id) 유니크 추가
--
-- 한 투자에 같은 섹터가 두 번 들어가면 총 투자금 합계와 섹터별 손익 통계가 모두 어긋난다.
-- 지금은 애그리거트 루트의 전체 교체 로직만 이를 막고 있어, 저장 경로가 하나 늘어나면 조용히 깨진다.
--
-- 적용 전 운영 데이터에서 확인함: 중복 (investment_id, sector_id) 조합 0건.
ALTER TABLE investment_sector
    ADD CONSTRAINT uk_investment_sector_investment_sector UNIQUE (investment_id, sector_id);
