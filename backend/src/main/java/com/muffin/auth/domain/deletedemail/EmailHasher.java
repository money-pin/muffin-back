package com.muffin.auth.domain.deletedemail;

/** 이메일을 안전하게 해시하는 능력의 포트. 실제 알고리즘(HMAC-SHA256)은 infrastructure가 구현. */
public interface EmailHasher {

    String hash(String rawEmail);
}
