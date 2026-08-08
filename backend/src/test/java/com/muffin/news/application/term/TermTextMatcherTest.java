package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TermTextMatcherTest {

    @Test
    void containsTerm_matchesWhitespaceVariants() {
        assertThat(TermTextMatcher.containsTerm("신재생에너지가 확대되고 있습니다.", "신 재생에너지"))
                .isTrue();
        assertThat(TermTextMatcher.containsTerm("장기 침체 우려가 커졌습니다.", "장기침체")).isTrue();
    }

    @Test
    void containsTerm_matchesParenthesisAliases() {
        String term = "보통주자본(Common Equity Tier 1)";

        assertThat(TermTextMatcher.containsTerm("보통주자본 비율이 중요합니다.", term)).isTrue();
        assertThat(TermTextMatcher.containsTerm("Common Equity Tier 1 비율이 중요합니다.", term))
                .isTrue();
    }

    @Test
    void findMatches_returnsOriginalContentRange() {
        assertThat(TermTextMatcher.findMatches("장기 침체 우려가 커졌습니다.", "장기침체"))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.start()).isZero();
                    assertThat(match.end()).isEqualTo("장기 침체".length());
                });
    }
}
