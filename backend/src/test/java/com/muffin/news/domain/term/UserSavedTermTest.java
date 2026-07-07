package com.muffin.news.domain.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserSavedTermTest {

    @Test
    @DisplayName("사용자 저장 용어 레코드를 생성한다")
    void create_createsUserSavedTerm() {
        UserSavedTerm userSavedTerm = UserSavedTerm.create(1L, 10L);

        assertEquals(1L, userSavedTerm.getUserId());
        assertEquals(10L, userSavedTerm.getTermId());
    }
}
