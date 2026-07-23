package com.muffin.user.domain;

/** 닉네임에 비속어가 포함되어 있는지 판단하는 능력의 포트. 실제 판정 방식(사전/외부 API 등)은 infrastructure가 구현. */
public interface NicknameProfanityPolicy {

    boolean isProfane(String normalizedNickname);
}
