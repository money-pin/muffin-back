package com.muffin.notification.application.dispatch;

/**
 * 기기에 보낼 알림 한 건의 내용.
 *
 * @param deepLink 알림을 탭했을 때 앱이 열 화면 (muffin://...)
 */
public record PushMessage(String title, String body, String deepLink) {}
