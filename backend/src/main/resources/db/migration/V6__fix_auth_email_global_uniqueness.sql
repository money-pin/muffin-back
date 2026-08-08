-- uk_provider_email(provider, email)은 provider가 다르면 같은 이메일의 중복 가입을 막지 못한다.
-- 예: LOCAL 회원가입과 GOOGLE 회원가입이 같은 이메일로 동시에 들어오면 이 제약을 통과해 중복 계정이 생성될 수 있다.
-- 계정 연동(하나의 email에 여러 provider Auth를 묶는 기능)이 없는 현재 구조에서는 email 자체가 전역으로 유일해야 하므로
-- 제약 범위를 provider+email에서 email 단독으로 좁힌다.
ALTER TABLE `auth` DROP INDEX `uk_provider_email`;
ALTER TABLE `auth` ADD UNIQUE KEY `uk_email` (`email`);
