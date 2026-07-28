-- category seed: RSS 수집이 참조하는 경제/증권/세계 3종 카테고리 추가.
-- name 값은 application.yml의 muffin.news.rss.feeds[].category와 정확히 일치해야 한다.
-- RssFeedWriter가 findByName으로 조회해 없으면 예외를 던지므로, 이 시드가 없으면 뉴스가 한 건도 저장되지 않는다.
-- fallback_thumbnail_url은 원본 썸네일이 없을 때의 기본 이미지를 프론트가 카테고리별 자체 에셋으로
-- 렌더링하는 정책이라 NULL로 둔다.
-- INSERT IGNORE는 UNIQUE 위반뿐 아니라 데이터 변환/제약조건 오류까지 경고로 강등시켜
-- 재실행 시 실제 오류를 은폐할 수 있으므로, category.name의 uk_category_name 충돌만
-- 무시하는 ON DUPLICATE KEY UPDATE로 제한한다.
INSERT INTO `category` (`name`, `fallback_thumbnail_url`, `created_at`)
VALUES
  ('경제', NULL, NOW(6)),
  ('증권', NULL, NOW(6)),
  ('세계', NULL, NOW(6)) AS new_category
ON DUPLICATE KEY UPDATE `name` = new_category.name;
