package com.muffin.auth.domain;

/** 도메인이 필요로 하는 문자열을 암호화/대조하는 능력의 포트. 실제 알고리즘(BCrypt)은 infrastructure가 구현. */
public interface PasswordEncoder {

    String encode(String raw);

    boolean matches(String raw, String encoded);
}
