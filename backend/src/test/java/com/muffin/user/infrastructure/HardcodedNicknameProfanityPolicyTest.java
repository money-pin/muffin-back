package com.muffin.user.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HardcodedNicknameProfanityPolicyTest {

    private final HardcodedNicknameProfanityPolicy policy = new HardcodedNicknameProfanityPolicy();

    @Test
    @DisplayName("금칙어를 포함하면 true")
    void containsBannedWord() {
        assertThat(policy.isProfane("이건개새끼야")).isTrue();
    }

    @Test
    @DisplayName("영문 금칙어는 대소문자 구분 없이 판정")
    void caseInsensitiveEnglishWord() {
        assertThat(policy.isProfane("FUCKnickname")).isTrue();
    }

    @Test
    @DisplayName("금칙어가 없으면 false")
    void clean() {
        assertThat(policy.isProfane("착한닉네임")).isFalse();
    }
}
