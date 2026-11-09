-- 로그인 시각. 기기 토큰 등록 때 다른 기기 로그인·로그아웃 전에 발급된(옛 세션의) access token을 거르는 기준이다.
-- refresh token 재발급(rotate) 때는 바뀌지 않는다. 기존 행은 마지막 발급 시각으로 채운다.

ALTER TABLE `refresh_token` ADD COLUMN `session_started_at` datetime(6) DEFAULT NULL;

UPDATE `refresh_token` SET `session_started_at` = COALESCE(`updated_at`, `created_at`);

ALTER TABLE `refresh_token` MODIFY `session_started_at` datetime(6) NOT NULL;
