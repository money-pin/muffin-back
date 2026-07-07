package com.muffin.scrap.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 사용자-뉴스 유일성(한 뉴스 중복 스크랩 방지) DB 유니크 제약 검증. 도메인 순수 단위 테스트로는 확인할 수 없어 실제 저장을 시도한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScrapUniqueConstraintTest {

    @Autowired
    private ScrapRepository scrapRepository;

    @Test
    @DisplayName("같은 사용자-뉴스로 스크랩을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void scrap_duplicateUserAndNews_violatesUnique() {
        scrapRepository.saveAndFlush(Scrap.create(1L, 1024L));

        assertThrows(
                DataIntegrityViolationException.class, () -> scrapRepository.saveAndFlush(Scrap.create(1L, 1024L)));
    }
}
