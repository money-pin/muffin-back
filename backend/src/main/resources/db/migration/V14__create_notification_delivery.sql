-- 푸시 알림(NOTI-01~05)의 사용자별 발송 이력. 기기 토큰은 별도 PR에서 연동한다.
-- V12, V13은 먼저 열린 PR(#241, #247)이 사용 중이라 V14로 둔다. out-of-order를 쓰지 않으므로 이 파일이 먼저 머지되면
-- 그 PR들의 번호를 V14 뒤로 옮겨야 한다.
-- 상태·종류 컬럼은 briefing과 같은 이유로 enum 대신 varchar로 둔다.

CREATE TABLE `notification_delivery` (
  `notification_delivery_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dedup_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `deep_link` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `failure_reason` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sent_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`notification_delivery_id`),
  -- 배치 재실행·이벤트 중복에도 사용자당 1회만 발송되게 하는 락 역할을 겸한다.
  UNIQUE KEY `uk_notification_delivery_dedup_key` (`dedup_key`),
  KEY `idx_notification_delivery_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
