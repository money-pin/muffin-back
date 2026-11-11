package com.muffin.notification.domain.devicetoken;

/** Xcode로 설치한 빌드는 SANDBOX, TestFlight·App Store 빌드는 PRODUCTION 토큰을 받는다. 다른 쪽 서버로 보내면 거절된다. */
public enum ApnsEnvironment {
    SANDBOX,
    PRODUCTION
}
