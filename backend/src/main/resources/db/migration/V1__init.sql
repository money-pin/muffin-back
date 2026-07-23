/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `auth` (
  `auth_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email_verified` bit(1) NOT NULL,
  `failed_login_attempts` int NOT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` enum('GOOGLE','LOCAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider_user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`auth_id`),
  UNIQUE KEY `uk_provider_email` (`provider`,`email`),
  UNIQUE KEY `uk_provider_user` (`provider`,`provider_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `category_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `fallback_thumbnail_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`category_id`),
  UNIQUE KEY `uk_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `character_profile` (
  `character_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `muffin_type` enum('BUTTER','PLAIN','SPRINKLE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`character_id`),
  UNIQUE KEY `UK38jy6txpxuj1idl5ho4m9fine` (`muffin_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `character_recommended_sector` (
  `recommended_sector_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `character_id` bigint NOT NULL,
  `sector_id` bigint NOT NULL,
  PRIMARY KEY (`recommended_sector_id`),
  UNIQUE KEY `uk_character_recommended_sector_character_sector` (`character_id`,`sector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `deleted_emails` (
  `deleted_email_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) NOT NULL,
  `email_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`deleted_email_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_verification` (
  `email_verification_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `attempt_count` int NOT NULL,
  `code_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `verified` bit(1) NOT NULL,
  PRIMARY KEY (`email_verification_id`),
  KEY `idx_email_verification_email_created_at` (`email`,`created_at`),
  KEY `idx_email_verification_verified_expires_at` (`verified`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `etf` (
  `etf_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `etf_code` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `etf_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`etf_id`),
  UNIQUE KEY `uk_etf_etf_code` (`etf_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `etf_price` (
  `etf_prices_id` bigint NOT NULL AUTO_INCREMENT,
  `end_price` bigint DEFAULT NULL,
  `end_price_status` enum('FAILED','FINAL_MISSING','MARKET_CLOSED','NO_DATA','PENDING','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `etf_id` bigint NOT NULL,
  `price_date` date NOT NULL,
  `start_price` bigint DEFAULT NULL,
  `start_price_status` enum('FAILED','FINAL_MISSING','MARKET_CLOSED','NO_DATA','PENDING','SUCCESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`etf_prices_id`),
  UNIQUE KEY `uk_etf_price_etf_price_date` (`etf_id`,`price_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment` (
  `investment_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `finalized_at` datetime(6) DEFAULT NULL,
  `invest_date` date NOT NULL,
  `settled_at` datetime(6) DEFAULT NULL,
  `settlement_status` enum('CANCELLED','FAILED','NO_SETTLEMENT','PENDING','SETTLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CONFIRMED','NO_INVEST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_amount` bigint NOT NULL,
  `total_profit_loss` bigint NOT NULL,
  `total_profit_loss_rate` decimal(9,4) NOT NULL,
  `user_asset_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`investment_id`),
  UNIQUE KEY `uk_investment_user_invest_date` (`user_id`,`invest_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investment_sector` (
  `investment_sector_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `amount` bigint NOT NULL,
  `buy_price` decimal(19,4) DEFAULT NULL,
  `price_data_source` enum('FALLBACK_ZERO','NORMAL') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profit_loss` bigint DEFAULT NULL,
  `profit_loss_rate` decimal(9,4) DEFAULT NULL,
  `quantity` int NOT NULL,
  `sector_id` bigint NOT NULL,
  `sell_price` decimal(19,4) DEFAULT NULL,
  `investment_id` bigint NOT NULL,
  PRIMARY KEY (`investment_sector_id`),
  KEY `FK4np06y6o0xlrlmnyaxgqgig5c` (`investment_id`),
  CONSTRAINT `FK4np06y6o0xlrlmnyaxgqgig5c` FOREIGN KEY (`investment_id`) REFERENCES `investment` (`investment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member` (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `character_id` bigint DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `name` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `nickname` varchar(6) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `onboarding_completed` bit(1) NOT NULL,
  `role` enum('ADMIN','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','SUSPENDED','WITHDRAWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `term_agreed_at` datetime(6) DEFAULT NULL,
  `term_agreement` bit(1) NOT NULL,
  `user_uuid` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UK43bulm3osrfhjwr1wscw5glnb` (`user_uuid`),
  UNIQUE KEY `UKhh9kg6jti4n1eoiertn2k6qsc` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news` (
  `news_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `category_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `deleted_at` datetime(6) DEFAULT NULL,
  `original_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `published_at` datetime(6) NOT NULL,
  `publisher` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('FAILED','PENDING','PROCESSING','PUBLISHED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `thumbnail_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_count` bigint NOT NULL,
  PRIMARY KEY (`news_id`),
  UNIQUE KEY `uk_news_original_url` (`original_url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_explanation` (
  `news_explanation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `card_order` int NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `key_term` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `news_id` bigint NOT NULL,
  `status` enum('DONE','FAILED','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`news_explanation_id`),
  UNIQUE KEY `uk_news_explanation_news_order` (`news_id`,`card_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_sector_impact` (
  `news_sector_impact_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `impact` enum('NEGATIVE','NEUTRAL','POSITIVE','STRONG_NEGATIVE','STRONG_POSITIVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `news_id` bigint NOT NULL,
  `sector_id` bigint NOT NULL,
  PRIMARY KEY (`news_sector_impact_id`),
  UNIQUE KEY `uk_news_sector_impact_news_sector` (`news_id`,`sector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `news_term` (
  `news_term_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `term_id` bigint NOT NULL,
  `news_id` bigint NOT NULL,
  PRIMARY KEY (`news_term_id`),
  UNIQUE KEY `uk_news_term_news_term` (`news_id`,`term_id`),
  CONSTRAINT `FKqpqt55c6ra3val6vfy9rfrhna` FOREIGN KEY (`news_id`) REFERENCES `news` (`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_setting` (
  `user_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `investment_result_push_enabled` bit(1) NOT NULL,
  `news_update_push_enabled` bit(1) NOT NULL,
  `quiz_push_enabled` bit(1) NOT NULL,
  `ranking_changed_push_enabled` bit(1) NOT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `profit_summary` (
  `profit_summary_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `cumulative_profit_loss` bigint NOT NULL,
  `daily_profit_loss` bigint NOT NULL,
  `daily_profit_loss_rate` decimal(9,4) NOT NULL,
  `summary_date` date NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`profit_summary_id`),
  UNIQUE KEY `uk_profit_summary_user_summary_date` (`user_id`,`summary_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz` (
  `quiz_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `difficulty` enum('EASY','MEDIUM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `news_id` bigint NOT NULL,
  `question` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `quiz_order` int NOT NULL,
  `reward_money` bigint NOT NULL,
  `source_sentence` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `daily_quiz_set_id` bigint NOT NULL,
  PRIMARY KEY (`quiz_id`),
  UNIQUE KEY `uk_quiz_set_order` (`daily_quiz_set_id`,`quiz_order`),
  CONSTRAINT `FKhpspgyi2cixtc9e482u2u8u68` FOREIGN KEY (`daily_quiz_set_id`) REFERENCES `quiz_set` (`daily_quiz_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_attempt` (
  `attempt_id` bigint NOT NULL AUTO_INCREMENT,
  `is_correct` bit(1) NOT NULL,
  `option_id` bigint NOT NULL,
  `quiz_id` bigint NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `quiz_session_id` bigint NOT NULL,
  PRIMARY KEY (`attempt_id`),
  UNIQUE KEY `uk_quiz_attempt_session_quiz` (`quiz_session_id`,`quiz_id`),
  CONSTRAINT `FKovnr6ty7q0gjf3pb84rlbvjy1` FOREIGN KEY (`quiz_session_id`) REFERENCES `quiz_session` (`quiz_session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_option` (
  `option_id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_correct` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `option_no` int NOT NULL,
  `quiz_id` bigint NOT NULL,
  PRIMARY KEY (`option_id`),
  UNIQUE KEY `uk_quiz_option_quiz_option_no` (`quiz_id`,`option_no`),
  CONSTRAINT `FK134e3ro35naxwfjqcda4ckljn` FOREIGN KEY (`quiz_id`) REFERENCES `quiz` (`quiz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_session` (
  `quiz_session_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `correct_count` int NOT NULL,
  `daily_quiz_set_id` bigint NOT NULL,
  `date` date NOT NULL,
  `reward_claimed` bit(1) NOT NULL,
  `reward_money` bigint NOT NULL,
  `solved_count` int NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('FINISHED','NOT_STARTED','PROGRESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `total_count` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`quiz_session_id`),
  UNIQUE KEY `uk_quiz_session_user_quiz_set` (`user_id`,`daily_quiz_set_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quiz_set` (
  `daily_quiz_set_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `quiz_date` date NOT NULL,
  `status` enum('GENERATING','PUBLISHED','READY','UNAVAILABLE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`daily_quiz_set_id`),
  UNIQUE KEY `uk_quiz_set_date` (`quiz_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `read_history` (
  `read_history_id` bigint NOT NULL AUTO_INCREMENT,
  `news_id` bigint NOT NULL,
  `read_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`read_history_id`),
  UNIQUE KEY `uk_read_history_user_news` (`user_id`,`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_token` (
  `refresh_token_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`refresh_token_id`),
  UNIQUE KEY `uk_refresh_token_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `scrap` (
  `scrap_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `news_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`scrap_id`),
  UNIQUE KEY `uk_scrap_user_news` (`user_id`,`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sector` (
  `sector_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `etf_id` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sector_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sector_group_id` bigint NOT NULL,
  `sector_order` int NOT NULL,
  PRIMARY KEY (`sector_id`),
  UNIQUE KEY `uk_sector_sector_code` (`sector_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sector_group` (
  `sector_group_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `group_code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `group_order` int NOT NULL,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`sector_group_id`),
  UNIQUE KEY `uk_sector_group_group_code` (`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `term_dictionary` (
  `term_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `term` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`term_id`),
  UNIQUE KEY `uk_term_dictionary_term` (`term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_asset` (
  `user_asset_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `daily_change_amount` bigint NOT NULL,
  `daily_change_rate` decimal(19,4) NOT NULL,
  `last_settled_at` datetime(6) DEFAULT NULL,
  `total_asset` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`user_asset_id`),
  UNIQUE KEY `uk_user_asset_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_onboarding` (
  `onboarding_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `first_question` int NOT NULL,
  `second_question` int NOT NULL,
  `third_question` int NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`onboarding_id`),
  UNIQUE KEY `UK8ki2uhx9yu395eca7k0o4i5h` (`user_id`),
  CONSTRAINT `FK42i9u8y5qlktaylr0qnomntll` FOREIGN KEY (`user_id`) REFERENCES `member` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_saved_term` (
  `user_saved_term_id` bigint NOT NULL AUTO_INCREMENT,
  `saved_at` datetime(6) NOT NULL,
  `term_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_saved_term_id`),
  UNIQUE KEY `uk_user_saved_term_user_term` (`user_id`,`term_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `weekly_ranking` (
  `weekly_ranking_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `nickname_snapshot` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `percentile` int DEFAULT NULL,
  `ranking_position` int NOT NULL,
  `user_id` bigint NOT NULL,
  `week_of_year` int NOT NULL,
  `week_start_date` date NOT NULL,
  `weekly_profit` bigint NOT NULL,
  `weekly_profit_rate` decimal(5,2) DEFAULT NULL,
  PRIMARY KEY (`weekly_ranking_id`),
  UNIQUE KEY `uk_weekly_ranking_user_week_start_date` (`user_id`,`week_start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

