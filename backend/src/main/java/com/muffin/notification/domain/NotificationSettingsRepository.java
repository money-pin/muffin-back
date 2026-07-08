package com.muffin.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** NotificationSettings 애그리거트 리포지토리 */
public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {}
