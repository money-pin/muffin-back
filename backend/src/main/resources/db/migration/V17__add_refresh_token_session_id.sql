ALTER TABLE `refresh_token` ADD COLUMN `session_id` varchar(36) DEFAULT NULL;
UPDATE `refresh_token` SET `session_id` = UUID();
ALTER TABLE `refresh_token` MODIFY `session_id` varchar(36) NOT NULL;
CREATE INDEX `idx_refresh_token_hash` ON `refresh_token` (`token_hash`);
