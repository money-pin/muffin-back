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
    void containsTerm_doesNotMatchAsciiAliasInsideLongerWord() {
        String term = "기업어음(CP)";

        assertThat(TermTextMatcher.containsTerm("BCPE 팡케아 케이맨2에 투자했습니다.", term)).isFalse();
        assertThat(TermTextMatcher.containsTerm("ABCP 발행이 늘었습니다.", term)).isFalse();
        assertThat(TermTextMatcher.containsTerm("기업은 CP 발행으로 단기자금을 조달했습니다.", term))
                .isTrue();
        assertThat(TermTextMatcher.containsTerm("CP is issued by the company.", term))
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

    /**
     * 한국어는 수식어가 앞에 붙어 복합어를 만든다. 앞 글자가 한글이면 더 긴 낱말의 일부이므로 매칭하지 않는다.
     *
     * <p>"마찬가지로"와 "규정상 금지로"에서 용어 {@code 지로(GIRO)}의 별칭 {@code 지로}가 걸려 본문에 밑줄이 그어지고,
     * 그 연결이 오늘의 용어 후보까지 올라갔다.
     */
    @Test
    void containsTerm_doesNotMatchHangulAliasGluedToPrecedingWord() {
        String term = "지로(GIRO)";

        assertThat(TermTextMatcher.containsTerm("관련 광고는 규정상 금지로 집행하지 않았습니다.", term))
                .isFalse();
        assertThat(TermTextMatcher.containsTerm("외환스왑과 마찬가지로 원금을 교환합니다.", term)).isFalse();
        assertThat(TermTextMatcher.containsTerm("무이표채는 이자를 따로 주지 않습니다.", "이표채")).isFalse();
        assertThat(TermTextMatcher.containsTerm("신흥시장국채권지수가 올랐습니다.", "국채")).isFalse();
    }

    /** 조사와 어미는 뒤에 붙는다. 뒤쪽까지 막으면 정상 표기를 전부 놓친다. */
    @Test
    void containsTerm_matchesHangulTermFollowedByParticle() {
        assertThat(TermTextMatcher.containsTerm("지로 용지로 납부했습니다.", "지로(GIRO)")).isTrue();
        assertThat(TermTextMatcher.containsTerm("결제가 완료되었습니다.", "결제")).isTrue();
        assertThat(TermTextMatcher.containsTerm("회사가 청산될 예정입니다.", "청산")).isTrue();
        assertThat(TermTextMatcher.containsTerm("기준금리를 올렸습니다.", "기준금리")).isTrue();
    }

    /** 문장 첫머리, 괄호나 따옴표 뒤처럼 앞 글자가 한글이 아니면 그대로 매칭한다. */
    @Test
    void containsTerm_matchesHangulTermAtNonHangulBoundary() {
        assertThat(TermTextMatcher.containsTerm("지로는 소액대량 지급수단입니다.", "지로(GIRO)"))
                .isTrue();
        assertThat(TermTextMatcher.containsTerm("수단(지로)으로 납부합니다.", "지로(GIRO)")).isTrue();
        assertThat(TermTextMatcher.containsTerm("GIRO 지로 방식입니다.", "지로(GIRO)")).isTrue();
    }

    /** 앞 글자가 한글이라 걸러진 자리에서도, 복합어 자체가 사전에 있으면 그 용어가 대신 잡힌다. */
    @Test
    void findMatches_stillMatchesCompoundTermItself() {
        assertThat(TermTextMatcher.findMatches("지급결제시스템을 통해 처리합니다.", "지급결제시스템")).hasSize(1);
        assertThat(TermTextMatcher.containsTerm("지급결제시스템을 통해 처리합니다.", "결제")).isFalse();
    }
}
