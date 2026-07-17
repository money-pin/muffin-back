package com.muffin.auth.domain;

/** 검증된 구글 ID Token에서 뽑아낸 클레임. sub가 구글 계정의 고유 식별자(providerUserId)다. */
public record GoogleIdTokenPayload(String sub, String email, String name) {}
