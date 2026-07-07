package com.muffin.news.domain.readhistory;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 사용자-뉴스 유일성(뉴스별 1건) DB 유니크 제약 검증.
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReadHistoryUniqueConstraintTest {

    @Autowired
    private ReadHistoryRepository readHistoryRepository;

    @Test
    @DisplayName("같은 사용자-뉴스로 열람 기록을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void readHistory_duplicateUserAndNews_violatesUnique() {
        readHistoryRepository.saveAndFlush(ReadHistory.create(1L, 1024L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> readHistoryRepository.saveAndFlush(ReadHistory.create(1L, 1024L)));
    }
}
