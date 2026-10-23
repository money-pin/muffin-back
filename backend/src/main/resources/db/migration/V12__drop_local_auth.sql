-- 로컬(이메일/비밀번호) 로그인과 이메일 인증을 제거하고 소셜 로그인(구글/카카오/애플)만 남긴다.
--
-- 계정은 provider+provider_user_id(sub)로만 식별한다. 이메일은 참고 정보가 되어 전역 유일성(uk_email)을 없애고,
-- 카카오처럼 사용자가 이메일 제공을 거부할 수 있는 provider가 있어 NULL을 허용한다.
-- 계정 연동이 없으므로 유저 한 명은 Auth를 하나만 가진다(uk_auth_user_id).
--
-- LOCAL 행은 로그인 경로가 사라져 더 이상 쓸 수 없다. 운영에는 LOCAL 계정이 없어 로컬 개발 DB 정리용이다.
DELETE FROM `auth` WHERE `provider` = 'LOCAL';

DROP TABLE `email_verification`;

ALTER TABLE `auth`
  DROP INDEX `uk_email`,
  DROP COLUMN `password`,
  DROP COLUMN `failed_login_attempts`,
  DROP COLUMN `locked_until`,
  DROP COLUMN `email_verified`,
  MODIFY `provider` enum('GOOGLE','KAKAO','APPLE') COLLATE utf8mb4_unicode_ci NOT NULL,
  MODIFY `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  MODIFY `provider_user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  ADD UNIQUE KEY `uk_auth_user_id` (`user_id`);
