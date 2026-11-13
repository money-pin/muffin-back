-- 기사 한 건이 섹터를 올릴/내릴 것으로 보는 이유를 함께 저장한다.
--
-- 영향도 등급(POSITIVE/NEGATIVE 등)만으로는 "왜 그런지"를 알 수 없어 화면에서 설명할 근거가 없었다.
-- 재구성 AI가 등급을 판단할 때 쓴 근거를 그대로 받아 둔다.
--
-- NULL을 허용하는 이유는 세 가지다. 이 컬럼이 생기기 전에 분석된 기사에는 이유가 없고,
-- NEUTRAL 섹터는 설명할 영향 자체가 없으며, AI가 이유를 빠뜨려도 등급은 그대로 보여줄 수 있어야 한다.
ALTER TABLE `news_sector_impact`
  ADD COLUMN `reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `impact`;
