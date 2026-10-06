package com.muffin.news.application.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RssSelectionStrategyTest {

    private final RssArticleSelector articleSelector = mock(RssArticleSelector.class);
    private final UnifiedRssArticleSelector unifiedSelector = mock(UnifiedRssArticleSelector.class);
    private final RssSelectionStrategy perCategory = new PerCategoryRssSelectionStrategy(articleSelector);
    private final RssSelectionStrategy unified = new UnifiedRssSelectionStrategy(unifiedSelector, perCategory);

    @Test
    @DisplayName("카테고리별 선별은 카테고리마다 따로 평가한다")
    void perCategory_selectsWithinEachCategory() {
        RssArticle economy = article("경제 기사", "https://example.com/1");
        RssArticle stock = article("증권 기사", "https://example.com/2");
        when(articleSelector.select("경제", List.of(economy))).thenReturn(List.of(economy));
        when(articleSelector.select("증권", List.of(stock))).thenReturn(List.of(stock));

        Map<String, List<RssArticle>> result = perCategory.select(Map.of("경제", List.of(economy), "증권", List.of(stock)));

        assertThat(result).containsOnlyKeys("경제", "증권");
    }

    /** 한 카테고리의 AI 선별 실패가 다른 카테고리까지 버리면 그날 수집이 통째로 빈다. */
    @Test
    @DisplayName("카테고리별 선별은 한 카테고리가 실패해도 나머지를 저장한다")
    void perCategory_isolatesFailurePerCategory() {
        RssArticle economy = article("경제 기사", "https://example.com/1");
        RssArticle stock = article("증권 기사", "https://example.com/2");
        when(articleSelector.select("경제", List.of(economy))).thenThrow(new IllegalStateException("ai down"));
        when(articleSelector.select("증권", List.of(stock))).thenReturn(List.of(stock));

        Map<String, List<RssArticle>> result = perCategory.select(Map.of("경제", List.of(economy), "증권", List.of(stock)));

        assertThat(result).containsOnlyKeys("증권");
    }

    @Test
    @DisplayName("통합 선별은 모든 카테고리 후보를 한 번에 넘기고 결과를 카테고리별로 되묶는다")
    void unified_passesAllCandidatesAtOnceAndRegroups() {
        RssArticle economy = article("경제 기사", "https://example.com/1");
        RssArticle stock = article("증권 기사", "https://example.com/2");
        when(unifiedSelector.select(anyList()))
                .thenReturn(List.of(new CategorizedRssArticle("경제", economy), new CategorizedRssArticle("증권", stock)));

        Map<String, List<RssArticle>> result = unified.select(Map.of("경제", List.of(economy), "증권", List.of(stock)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategorizedRssArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(unifiedSelector).select(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(result).containsOnlyKeys("경제", "증권");
        assertThat(result.get("경제")).containsExactly(economy);
    }

    /** 통합 선별은 카테고리 균형을 맞추지 않으므로 한 카테고리가 통째로 빠질 수 있다. */
    @Test
    @DisplayName("통합 선별 결과에서 선택되지 않은 카테고리는 아예 빠진다")
    void unified_mayDropCategoryEntirely() {
        RssArticle economy = article("경제 기사", "https://example.com/1");
        RssArticle stock = article("증권 기사", "https://example.com/2");
        when(unifiedSelector.select(anyList())).thenReturn(List.of(new CategorizedRssArticle("경제", economy)));

        Map<String, List<RssArticle>> result = unified.select(Map.of("경제", List.of(economy), "증권", List.of(stock)));

        assertThat(result).containsOnlyKeys("경제");
    }

    /** 통합 선별은 AI 호출이 하루 한 번뿐이라, 그 한 번이 실패하면 카테고리별 선별로 떨어져 수집을 살린다. */
    @Test
    @DisplayName("통합 선별이 실패하면 카테고리별 선별로 폴백한다")
    void unified_fallsBackToPerCategoryOnFailure() {
        RssArticle economy = article("경제 기사", "https://example.com/1");
        when(unifiedSelector.select(anyList())).thenThrow(new IllegalStateException("ai down"));
        when(articleSelector.select("경제", List.of(economy))).thenReturn(List.of(economy));

        Map<String, List<RssArticle>> result = unified.select(Map.of("경제", List.of(economy)));

        assertThat(result).containsOnlyKeys("경제");
        verify(articleSelector).select("경제", List.of(economy));
    }

    @Test
    @DisplayName("후보가 없으면 AI를 부르지 않는다")
    void unified_skipsCallWhenNoCandidate() {
        assertThat(unified.select(Map.of())).isEmpty();

        verify(unifiedSelector, never()).select(anyList());
    }

    private static RssArticle article(String title, String url) {
        return new RssArticle(title, url, "요약", null, LocalDateTime.of(2026, 7, 12, 6, 0));
    }
}
