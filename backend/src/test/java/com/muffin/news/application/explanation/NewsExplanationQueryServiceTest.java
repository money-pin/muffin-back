package com.muffin.news.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsExplanationQueryServiceTest {

    private NewsRepository newsRepository;
    private NewsExplanationRepository newsExplanationRepository;
    private NewsExplanationQueryService newsExplanationQueryService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        newsExplanationRepository = mock(NewsExplanationRepository.class);
        newsExplanationQueryService = new NewsExplanationQueryService(newsRepository, newsExplanationRepository);
    }

    @Test
    @DisplayName("존재하지 않는 뉴스의 해설 카드를 조회하면 CONTENT_404_001 예외를 던진다")
    void getExplanationCards_throwsWhenNewsDoesNotExist() {
        Long newsId = 1L;
        when(newsRepository.existsById(newsId)).thenReturn(false);

        assertThatThrownBy(() -> newsExplanationQueryService.getExplanationCards(newsId))
                .isInstanceOf(NewsException.class)
                .extracting("errorCode")
                .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);

        verify(newsExplanationRepository, never())
                .findTop3ByNewsIdAndStatusOrderByCardOrderAsc(newsId, NewsExplanationStatus.DONE);
    }

    @Test
    @DisplayName("뉴스는 있지만 완료된 해설 카드가 없으면 빈 목록을 반환한다")
    void getExplanationCards_returnsEmptyListWhenDoneCardsDoNotExist() {
        Long newsId = 1L;
        when(newsRepository.existsById(newsId)).thenReturn(true);
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(newsId, NewsExplanationStatus.DONE))
                .thenReturn(List.of());

        NewsExplanationCardsResponse response = newsExplanationQueryService.getExplanationCards(newsId);

        assertThat(response.newsId()).isEqualTo(newsId);
        assertThat(response.cards()).isEmpty();
    }

    @Test
    @DisplayName("완료된 해설 카드를 cardOrder 순서로 최대 3개 조회해 응답으로 변환한다")
    void getExplanationCards_returnsDoneCards() {
        Long newsId = 1L;
        NewsExplanation first = NewsExplanation.create(newsId, 1, "기준금리란?", "기준금리 해설", "기준금리");
        NewsExplanation second = NewsExplanation.create(newsId, 2, "ETF란?", "ETF 해설", "ETF");

        when(newsRepository.existsById(newsId)).thenReturn(true);
        when(newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(newsId, NewsExplanationStatus.DONE))
                .thenReturn(List.of(first, second));

        NewsExplanationCardsResponse response = newsExplanationQueryService.getExplanationCards(newsId);

        assertThat(response.newsId()).isEqualTo(newsId);
        assertThat(response.cards()).hasSize(2);
        assertThat(response.cards().get(0).cardOrder()).isEqualTo(1);
        assertThat(response.cards().get(0).title()).isEqualTo("기준금리란?");
        assertThat(response.cards().get(0).keyTerm()).isEqualTo("기준금리");
        assertThat(response.cards().get(0).content()).isEqualTo("기준금리 해설");
        assertThat(response.cards().get(1).cardOrder()).isEqualTo(2);
    }
}
