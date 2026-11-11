-- 섹터 성적표 한 줄에 "왜 움직였는지"를 함께 저장한다.
--
-- 등락률과 같은 이유로 조회 시점에 다시 만들지 않고 저장해 둔다. 발행된 브리핑은 불변이어야 하는데,
-- 이 문장은 AI가 그날의 뉴스 후보를 보고 쓴 것이라 나중에 다시 생성하면 같은 날 브리핑의 설명이 달라진다.
--
-- NULL을 허용하는 이유는 두 가지다. 이 컬럼이 생기기 전에 발행된 브리핑에는 설명이 없고,
-- AI가 특정 섹터의 설명을 빠뜨려도 등락률만으로 성적표를 보여줄 수 있어야 하기 때문이다.
ALTER TABLE `briefing_sector_score`
  ADD COLUMN `change_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `change_rate`;
