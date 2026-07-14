package com.muffin.auth.domain;

/** 이미 사용 중인 이메일로 검증을 시도한 경우. */
public class DuplicateEmailException extends IllegalStateException {

    public DuplicateEmailException(String email) {
        super("이미 사용 중인 이메일입니다: " + email);
    }
}
