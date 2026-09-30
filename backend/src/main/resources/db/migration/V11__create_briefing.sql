-- 모닝 머핀 브리핑(CONTENT-09). 하루치 브리핑의 한 줄 요약, 이슈 3건, 섹터 성적표, 열람 기록을 담는다.
-- 상태 컬럼은 enum이 아니라 varchar로 둔다. 상태가 늘어날 때 ALTER ... MODIFY 없이 값만 추가하면 되고,
-- 엔티티의 @Column(length = 20)과도 정확히 일치한다.

CREATE TABLE `briefing` (
  `briefing_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `briefing_date` date NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `headline` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `term_id` bigint DEFAULT NULL,
  `term_summary` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`briefing_id`),
  -- 인스턴스 간 동시 생성을 막는 락 역할을 겸한다.
  UNIQUE KEY `uk_briefing_date` (`briefing_date`),
  -- 발행 대상 조회와 최근 7편 목록이 모두 (상태, 날짜)로 읽는다.
  KEY `idx_briefing_status_date` (`status`,`briefing_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `briefing_issue` (
  `briefing_issue_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `briefing_id` bigint NOT NULL,
  `issue_order` int NOT NULL,
  `news_id` bigint NOT NULL,
  `title` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `impact_line` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`briefing_issue_id`),
  UNIQUE KEY `uk_briefing_issue_briefing_order` (`briefing_id`,`issue_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `briefing_sector_score` (
  `briefing_sector_score_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `briefing_id` bigint NOT NULL,
  `sector_id` bigint NOT NULL,
  `change_rate` decimal(7,2) NOT NULL,
  `rank_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score_order` int NOT NULL,
  PRIMARY KEY (`briefing_sector_score_id`),
  UNIQUE KEY `uk_briefing_sector_score_briefing_sector` (`briefing_id`,`sector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `briefing_view` (
  `briefing_view_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `briefing_date` date NOT NULL,
  `viewed_at` datetime(6) NOT NULL,
  PRIMARY KEY (`briefing_view_id`),
  UNIQUE KEY `uk_briefing_view_user_date` (`user_id`,`briefing_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
