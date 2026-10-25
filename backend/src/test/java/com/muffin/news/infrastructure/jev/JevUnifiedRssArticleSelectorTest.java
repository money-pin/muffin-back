package com.muffin.news.infrastructure.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.application.rss.CategorizedRssArticle;
import com.muffin.news.application.rss.RssArticle;
import com.muffin.news.infrastructure.openai.AiSelectionProperties;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JevUnifiedRssArticleSelectorTest {

    private JevClient jevClient;
    private JevUnifiedRssArticleSelector selector;

    /** 기사 URL → 평가 결과. 테스트마다 필요한 기사만 채워 넣는다. */
    private final Map<String, Map<String, JevAnswer>> scores = new HashMap<>();

    @BeforeEach
    void setUp() {
        jevClient = mock(JevClient.class);
        selector = newSelector(defaultProperties());

        when(jevClient.evaluate(anyString(), any(), any())).thenAnswer(invocation -> {
            String label = invocation.getArgument(0);
            Map<String, JevAnswer> answer = scores.get(label);
            if (answer == null) {
                throw new IllegalStateException("no stub for " + label);
            }
            return answer;
        });
    }

    private JevUnifiedRssArticleSelector newSelector(JevProperties properties) {
        return new JevUnifiedRssArticleSelector(jevClient, properties, new AiSelectionProperties("gpt-5-mini", 5, 15));
    }

    private static JevProperties defaultProperties() {
        return new JevProperties(
                "test-key", "https://jev.test/v1/systemone", "jev-latest", 2, 3, 2, 1, 0.5, 1.0, 30, 0.85);
    }

    @Test
    @DisplayName("가중합이 높은 순서로 돌려준다")
    void select_ranksByWeightedTotal() {
        given("https://news/1", 1.0, 1.0, 0.0, 2.0, 0.0);
        given("https://news/2", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/3", 2.0, 1.0, 1.0, 2.5, 0.0);
        noDuplicates();

        List<CategorizedRssArticle> result = selector.select(List.of(
                candidate("경제", "https://news/1"),
                candidate("증권", "https://news/2"),
                candidate("세계", "https://news/3")));

        assertThat(result)
                .extracting(candidate -> candidate.article().url())
                .containsExactly("https://news/2", "https://news/3", "https://news/1");
    }

    /** 광고·부고·연예·스포츠·시황표는 선별 전에 버린다. */
    @Test
    @DisplayName("노이즈 판정이 임계값 이상이면 제외한다")
    void select_dropsNoisyArticle() {
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/2", 2.0, 1.0, 1.0, 2.0, 0.95);
        noDuplicates();

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("세계", "https://news/2")));

        assertThat(result).extracting(candidate -> candidate.article().url()).containsExactly("https://news/1");
    }

    /** 파급력이 아무리 높아도 등락 결과만 전하는 기사는 브리핑 재료가 아니다. */
    @Test
    @DisplayName("시황 결과 보도는 파급력이 높아도 제외한다")
    void select_dropsMarketRecapEvenWhenImpactIsHigh() {
        given("https://news/1", 2.0, 1.0, 1.0, 2.5, 0.0);
        // 파급력 최상위지만 causality가 컷오프 미만인 전형적인 시황 기사
        given("https://news/2", 3.0, 1.0, 1.0, 0.88, 0.0);
        noDuplicates();

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("증권", "https://news/2")));

        assertThat(result).extracting(candidate -> candidate.article().url()).containsExactly("https://news/1");
    }

    @Test
    @DisplayName("같은 사건으로 판정되면 점수가 높은 기사만 남긴다")
    void select_keepsOnlyBestArticleOfDuplicateEvent() {
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/2", 2.5, 2.0, 2.0, 3.0, 0.0);
        when(jevClient.evaluate(eq("same-event"), any(), any()))
                .thenReturn(Map.of("same_event", new JevAnswer("noul", 0, 0.93, 0.8)));

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("경제", "https://news/2")));

        assertThat(result).extracting(candidate -> candidate.article().url()).containsExactly("https://news/1");
    }

    /** 임계값 아래는 다른 사건이다. 애매한 쌍을 묶으면 서로 다른 기사가 사라진다. */
    @Test
    @DisplayName("같은 사건 확률이 임계값 미만이면 둘 다 남긴다")
    void select_keepsBothWhenSameEventProbabilityIsBelowThreshold() {
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/2", 2.5, 2.0, 2.0, 3.0, 0.0);
        when(jevClient.evaluate(eq("same-event"), any(), any()))
                .thenReturn(Map.of("same_event", new JevAnswer("noul", 0, 0.82, 0.5)));

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("경제", "https://news/2")));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("쌍 판정이 실패하면 중복으로 묶지 않고 둘 다 남긴다")
    void select_keepsBothWhenSameEventCheckFails() {
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/2", 2.5, 2.0, 2.0, 3.0, 0.0);
        when(jevClient.evaluate(eq("same-event"), any(), any())).thenThrow(new IllegalStateException("jev down"));

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("경제", "https://news/2")));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("중복 검사 풀 크기를 0으로 두면 쌍 판정을 하지 않는다")
    void select_skipsDeduplicationWhenPoolSizeIsZero() {
        selector = newSelector(new JevProperties(
                "test-key", "https://jev.test/v1/systemone", "jev-latest", 2, 3, 2, 1, 0.5, 1.0, 0, 0.85));
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        given("https://news/2", 2.5, 2.0, 2.0, 3.0, 0.0);

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("경제", "https://news/2")));

        assertThat(result).hasSize(2);
        verify(jevClient, never()).evaluate(eq("same-event"), any(), any());
    }

    /** 한 건의 평가 실패로 나머지를 버리면 그날 수집이 비어 버린다. */
    @Test
    @DisplayName("일부 기사의 평가가 실패해도 나머지는 선별한다")
    void select_continuesWhenSomeArticlesFail() {
        given("https://news/1", 3.0, 2.0, 2.0, 3.0, 0.0);
        // https://news/2는 스텁이 없어 평가가 실패한다
        noDuplicates();

        List<CategorizedRssArticle> result =
                selector.select(List.of(candidate("경제", "https://news/1"), candidate("증권", "https://news/2")));

        assertThat(result).extracting(candidate -> candidate.article().url()).containsExactly("https://news/1");
    }

    /** 전건 실패는 선별기 장애다. 예외를 던져야 전략이 카테고리별 선별로 폴백한다. */
    @Test
    @DisplayName("모든 기사의 평가가 실패하면 예외를 던진다")
    void select_throwsWhenEveryArticleFails() {
        assertThatThrownBy(() -> selector.select(List.of(candidate("경제", "https://news/1"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("전체 상한을 넘겨 돌려주지 않는다")
    void select_respectsMaxTotal() {
        selector = new JevUnifiedRssArticleSelector(
                jevClient, defaultProperties(), new AiSelectionProperties("gpt-5-mini", 5, 2));
        for (int index = 1; index <= 4; index++) {
            given("https://news/" + index, 3.0, 2.0, 2.0, 3.0, 0.0);
        }
        noDuplicates();

        List<CategorizedRssArticle> result = selector.select(List.of(
                candidate("경제", "https://news/1"),
                candidate("경제", "https://news/2"),
                candidate("경제", "https://news/3"),
                candidate("경제", "https://news/4")));

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("후보가 없으면 평가하지 않는다")
    void select_returnsEmptyWithoutCallingJev() {
        assertThat(selector.select(List.of())).isEmpty();

        verify(jevClient, never()).evaluate(anyString(), any(), any());
    }

    private void given(
            String url, double marketImpact, double dailyLife, double learningValue, double causality, double noise) {
        scores.put(
                url,
                Map.of(
                        "market_impact", new JevAnswer("score", marketImpact, 0, 0.8),
                        "daily_life", new JevAnswer("score", dailyLife, 0, 0.8),
                        "learning_value", new JevAnswer("score", learningValue, 0, 0.8),
                        "causality", new JevAnswer("score", causality, 0, 0.8),
                        "is_noise", new JevAnswer("noul", 0, noise, 0.8)));
    }

    private void noDuplicates() {
        when(jevClient.evaluate(eq("same-event"), any(), any()))
                .thenReturn(Map.of("same_event", new JevAnswer("noul", 0, 0.1, 0.9)));
    }

    private static CategorizedRssArticle candidate(String category, String url) {
        return new CategorizedRssArticle(
                category, new RssArticle("제목 " + url, url, "요약", null, LocalDateTime.of(2026, 10, 7, 6, 0)));
    }
}
