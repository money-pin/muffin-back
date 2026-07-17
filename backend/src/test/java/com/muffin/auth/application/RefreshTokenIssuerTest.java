package com.muffin.auth.application;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.RefreshToken;
import com.muffin.auth.domain.RefreshTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RefreshTokenIssuerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
    }

    @Test
    @DisplayName("처음 발급하면 행이 하나 생성되고, 저장된 해시는 리턴된 원문의 해시와 같다")
    void issue_createsRowWithMatchingHash() {
        String rawToken = refreshTokenIssuer.issue(USER_ID);

        RefreshToken saved = refreshTokenRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenIssuer.hash(rawToken));
        assertThat(saved.isExpired()).isFalse();
    }

    @Test
    @DisplayName("같은 유저에게 두 번 발급하면 행이 늘지 않고 기존 행이 회전된다")
    void issue_twiceForSameUser_rotatesInsteadOfCreatingNewRow() {
        String firstToken = refreshTokenIssuer.issue(USER_ID);
        String secondToken = refreshTokenIssuer.issue(USER_ID);

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        RefreshToken saved = refreshTokenRepository.findByUserId(USER_ID).orElseThrow();
        assertThat(saved.getTokenHash()).isEqualTo(RefreshTokenIssuer.hash(secondToken));
        assertThat(saved.getTokenHash()).isNotEqualTo(RefreshTokenIssuer.hash(firstToken));
    }
}
