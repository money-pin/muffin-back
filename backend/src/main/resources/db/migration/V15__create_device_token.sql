-- 푸시 발송용 기기 토큰. 현재 로그인한 기기의 토큰과, 다른 기기 로그인으로 곧 로그아웃될 옛 기기의 토큰을 둔다.
-- expires_at이 NULL이면 현재 기기, 값이 있으면 옛 기기가 실제로 로그아웃되는 시각(마지막 access token 만료)까지만 보낸다.
-- token이 유니크라 같은 기기에서 계정을 바꾸면 행의 주인이 새 계정으로 옮겨간다.
-- environment(SANDBOX/PRODUCTION)는 briefing과 같은 이유로 enum 대신 varchar로 둔다.

CREATE TABLE `device_token` (
  `device_token_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`device_token_id`),
  UNIQUE KEY `uk_device_token_token` (`token`),
  KEY `idx_device_token_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
