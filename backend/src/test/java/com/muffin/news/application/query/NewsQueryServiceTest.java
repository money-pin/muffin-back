package com.muffin.news.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.scrap.domain.ScrapRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 뉴스 상세 조회 시 열람 기록(ReadHistory) 동시 생성 경합에 대한 방어 로직을 검증한다. */
class NewsQueryServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final NewsQueryRepository newsQueryRepository = mock(NewsQueryRepository.class);
    private final NewsSectorImpactRepository newsSectorImpactRepository = mock(NewsSectorImpactRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final ReadHistoryRepository readHistoryRepository = mock(ReadHistoryRepository.class);
    private final ScrapRepository scrapRepository = mock(ScrapRepository.class);
    private final NewsCursorCodec newsCursorCodec = mock(NewsCursorCodec.class);
    private final Clock clock = Clock.systemDefaultZone();

    private final NewsQueryService newsQueryService = new NewsQueryService(
            newsRepository,
            newsQueryRepository,
            newsSectorImpactRepository,
            categoryRepository,
            termDictionaryRepository,
            readHistoryRepository,
            scrapRepository,
            newsCursorCodec,
            clock);

    /**
     * 동시 조회로 두 요청이 모두 findByUserIdAndNewsId에서 빈 값을 본 뒤 저장을 시도하면, 나중에 flush되는 쪽은
     * (user_id, news_id) 유니크 제약을 위반한다. 이때 예외를 그대로 전파하지 않고 먼저 커밋된 레코드를 다시 조회해
     * readAt만 갱신해야 상세 조회 자체가 실패하지 않는다.
     */
    @Test
    void upsertReadHistory_recoversFromConcurrentDuplicateInsert() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));

        ReadHistory raceWinner = spy(ReadHistory.create(userId, newsId));
        when(readHistoryRepository.findByUserIdAndNewsId(userId, newsId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceWinner));
        when(readHistoryRepository.saveAndFlush(any(ReadHistory.class)))
                .thenThrow(new DataIntegrityViolationException("uk_read_history_user_news"));
        when(scrapRepository.existsByUserIdAndNewsId(userId, newsId)).thenReturn(false);
        when(categoryRepository.findById(news.getCategoryId())).thenReturn(Optional.empty());

        assertThatCode(() -> newsQueryService.getNewsDetail(userId, newsId)).doesNotThrowAnyException();

        verify(raceWinner).updateReadAt();
        verify(readHistoryRepository, never()).save(any(ReadHistory.class));
    }

    @Test
    void getNewsDetail_returnsHighlightedBodySegmentsForMappedTerms() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId, "한국은행은 기준금리를 올렸습니다. 기준금리는 대출 이자에 영향을 줍니다.");
        news.addTerm(10L);
        news.addTerm(20L);

        TermDictionary baseRate = term(10L, "기준금리");
        TermDictionary rate = term(20L, "금리");

        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(readHistoryRepository.findByUserIdAndNewsId(userId, newsId)).thenReturn(Optional.empty());
        when(scrapRepository.existsByUserIdAndNewsId(userId, newsId)).thenReturn(false);
        when(categoryRepository.findById(news.getCategoryId())).thenReturn(Optional.empty());
        when(termDictionaryRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(baseRate, rate));

        NewsDetailResponse response = newsQueryService.getNewsDetail(userId, newsId);

        assertThat(response.bodySegments())
                .extracting(NewsDetailResponse.BodySegment::type)
                .containsExactly("TEXT", "HIGHLIGHT", "TEXT", "HIGHLIGHT", "TEXT");
        assertThat(response.bodySegments())
                .extracting(NewsDetailResponse.BodySegment::text)
                .containsExactly("한국은행은 ", "기준금리", "를 올렸습니다. ", "기준금리", "는 대출 이자에 영향을 줍니다.");
        assertThat(response.bodySegments())
                .extracting(NewsDetailResponse.BodySegment::termId)
                .containsExactly(null, 10L, null, 10L, null);
    }

    private News publishedNews(Long newsId) {
        return publishedNews(newsId, "본문");
    }

    private News publishedNews(Long newsId, String content) {
        News news =
                News.processing(1L, "제목", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://news/" + newsId);
        news.completeReconstruction("요약", content);
        news.publish();
        return news;
    }

    private TermDictionary term(Long termId, String term) {
        TermDictionary dictionary = TermDictionary.create(term, term + " 설명");
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }
}
