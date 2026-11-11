package com.muffin.auth.domain;

/** 검증된 ID Token에서 뽑아낸 클레임. sub가 provider 안에서 계정의 고유 식별자(providerUserId)다. email/name은 없을 수 있다. */
public record SocialIdentity(String sub, String email, String name) {}
