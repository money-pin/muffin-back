-- 푸시 발송용 기기 토큰. refresh_token처럼 사용자당 1개(마지막 로그인 기기)만 둔다.
-- token도 유니크라 같은 기기에서 계정을 바꾸면 행의 주인이 새 계정으로 옮겨간다.
-- environment(SANDBOX/PRODUCTION)는 briefing과 같은 이유로 enum 대신 varchar로 둔다.

CREATE TABLE `device_token` (
  `device_token_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`device_token_id`),
  UNIQUE KEY `uk_device_token_user_id` (`user_id`),
  UNIQUE KEY `uk_device_token_token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
