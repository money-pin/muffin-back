-- 탈퇴 후 재가입 제한(30일)의 기준을 이메일에서 소셜 계정 식별자(provider+sub)로 바꾼다.
--
-- 기존에는 탈퇴해도 auth.provider_user_id(sub)가 남아, 같은 소셜 계정으로 다시 로그인하면 탈퇴한 Auth가 조회돼
-- 영구히 재가입할 수 없었다. 이제 탈퇴 시 sub를 익명화하고, 재가입 제한은 provider+sub의 HMAC 해시로 판단한다.

-- 기존 행은 이메일 해시라 새 기준(provider+sub 해시)과 절대 일치하지 않는다. 남겨도 의미가 없어 지운다.
-- 그 결과 이 마이그레이션 시점에 탈퇴 30일이 지나지 않은 계정은 곧바로 재가입할 수 있다(해시 키가 앱에만 있어
-- SQL로 새 해시를 만들 수 없음).
DELETE FROM `deleted_emails`;

RENAME TABLE `deleted_emails` TO `deleted_accounts`;

ALTER TABLE `deleted_accounts`
  RENAME COLUMN `deleted_email_id` TO `deleted_account_id`,
  RENAME COLUMN `email_hash` TO `identity_hash`;

-- 이미 탈퇴한 계정도 Auth.anonymize()와 같은 형태로 익명화해 같은 소셜 계정으로 재가입할 수 있게 한다.
UPDATE `auth` a
  JOIN `member` m ON m.`user_id` = a.`user_id`
SET a.`provider_user_id` = CONCAT('withdrawn-', a.`auth_id`),
    a.`email` = NULL
WHERE m.`status` = 'WITHDRAWN';
