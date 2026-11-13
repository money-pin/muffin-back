package com.muffin.news.domain.sectorimpact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsSectorImpactTest {

    @Test
    @DisplayName("뉴스 섹터 영향도를 생성할 수 있다")
    void create() {
        NewsSectorImpact impact = NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE, "안전자산 수요가 늘어요.");

        assertEquals(1L, impact.getNewsId());
        assertEquals(10L, impact.getSectorId());
        assertEquals(ImpactType.POSITIVE, impact.getImpact());
    }

    @Test
    @DisplayName("뉴스 섹터 영향도 생성 시 필수값이 없으면 예외가 발생한다")
    void create_throwsWhenRequiredValueIsNull() {
        assertThrows(
                IllegalArgumentException.class, () -> NewsSectorImpact.create(null, 10L, ImpactType.POSITIVE, null));
        assertThrows(
                IllegalArgumentException.class, () -> NewsSectorImpact.create(1L, null, ImpactType.POSITIVE, null));
        assertThrows(IllegalArgumentException.class, () -> NewsSectorImpact.create(1L, 10L, null, null));
    }

    /** 영향이 없다고 판단해 놓고 이유를 적는 것은 모순이다. 모델이 채워 넣어도 저장 단계에서 끊는다. */
    @Test
    @DisplayName("NEUTRAL이면 이유를 붙여도 버린다")
    void create_dropsReasonWhenNeutral() {
        NewsSectorImpact impact = NewsSectorImpact.create(1L, 10L, ImpactType.NEUTRAL, "영향이 없는데 이유를 적었어요.");

        assertNull(impact.getReason());
    }

    /** 중간에서 끊긴 문장이 화면에 나가면 설명이 없는 것보다 나쁘다. 잘라 붙이지 않고 버린다. */
    @Test
    @DisplayName("이유가 60자를 넘거나 줄바꿈이 섞이면 버린다")
    void create_dropsUnusableReason() {
        String tooLong = "가".repeat(61);

        assertNull(
                NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE, tooLong).getReason());
        assertNull(NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE, "첫 줄이에요.\n둘째 줄이에요.")
                .getReason());
        assertNull(NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE, "   ").getReason());
        assertNull(NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE, null).getReason());
    }

    @Test
    @DisplayName("쓸 수 있는 이유는 공백을 다듬어 그대로 남긴다")
    void create_keepsUsableReason() {
        NewsSectorImpact impact = NewsSectorImpact.create(1L, 10L, ImpactType.NEGATIVE, "  달러 강세로 눌려요.  ");

        assertEquals("달러 강세로 눌려요.", impact.getReason());
    }
}
