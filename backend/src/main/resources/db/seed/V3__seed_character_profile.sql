-- character_profile seed: plain/sprinkle/butter 3종 캐릭터 추가
-- description, image_url은 추후 확정되면 별도 마이그레이션으로 채운다.
INSERT INTO `character_profile` (`muffin_type`, `name`, `description`, `image_url`, `created_at`)
VALUES
  ('PLAIN', '플레인 머핀', NULL, NULL, NOW(6)),
  ('SPRINKLE', '스프링클 머핀', NULL, NULL, NOW(6)),
  ('BUTTER', '버터빛 머핀', NULL, NULL, NOW(6));
