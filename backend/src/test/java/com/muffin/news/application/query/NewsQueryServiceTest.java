package com.muffin.news.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import com.muffin.scrap.domain.ScrapRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** 뉴스 상세 조회와 열람 처리 로직을 검증한다. */
class NewsQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-08-01T03:00:00Z"), KST);

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final NewsQueryRepository newsQueryRepository = mock(NewsQueryRepository.class);
    private final NewsSectorImpactRepository newsSectorImpactRepository = mock(NewsSectorImpactRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final ReadHistoryRepository readHistoryRepository = mock(ReadHistoryRepository.class);
    private final ScrapRepository scrapRepository = mock(ScrapRepository.class);
    private final NewsCursorCodec newsCursorCodec = mock(NewsCursorCodec.class);
    private final Clock clock = FIXED_CLOCK;

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

    @Test
    void recordNewsRead_usesWriteLockAndCreatesReadHistoryWhenMissing() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId);
        when(newsRepository.findByIdForUpdate(newsId)).thenReturn(Optional.of(news));
        when(readHistoryRepository.findByUserIdAndNewsId(userId, newsId)).thenReturn(Optional.empty());

        assertThat(newsQueryService.recordNewsRead(userId, newsId).viewCount()).isEqualTo(1L);

        verify(readHistoryRepository).save(any(ReadHistory.class));
    }

    @Test
    void getNewsDetail_doesNotIncreaseViewCountOrSaveReadHistory() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(scrapRepository.existsByUserIdAndNewsId(userId, newsId)).thenReturn(false);
        when(categoryRepository.findById(news.getCategoryId())).thenReturn(Optional.empty());

        NewsDetailResponse response = newsQueryService.getNewsDetail(userId, newsId);

        assertThat(response.viewCount()).isZero();
        verifyNoInteractions(readHistoryRepository);
    }

    @Test
    void recordNewsRead_increasesViewCountAndUpdatesExistingReadAt() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId);
        ReadHistory readHistory = spy(ReadHistory.create(userId, newsId));
        when(newsRepository.findByIdForUpdate(newsId)).thenReturn(Optional.of(news));
        when(readHistoryRepository.findByUserIdAndNewsId(userId, newsId)).thenReturn(Optional.of(readHistory));

        assertThat(newsQueryService.recordNewsRead(userId, newsId).viewCount()).isEqualTo(1L);
        verify(readHistory).updateReadAt();
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

    /** 오늘의 뉴스 썸네일: 원본이 있으면 그 URL을, 없으면 null을 담는다(기본 이미지는 프론트가 처리). */
    @Test
    void getTodayNews_returnsOriginalThumbnailOrNull() {
        when(newsQueryRepository.findTodayPublishedNews(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(summaryRow(1L, null, true), summaryRow(2L, "https://origin/2.jpg", false)));

        NewsTodayResponse response = newsQueryService.getTodayNews(1L);

        assertThat(response.items())
                .extracting(NewsTodayResponse.NewsTodayItem::thumbnailUrl)
                .containsExactly(null, "https://origin/2.jpg");
        assertThat(response.items())
                .extracting(NewsTodayResponse.NewsTodayItem::isScrapped)
                .containsExactly(true, false);
    }

    @Test
    void getTodayNews_returnsTodayNewsWithoutFallbackWhenTodayNewsExists() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(newsQueryRepository.findTodayPublishedNews(1L, todayStart, todayStart.plusDays(1), 3))
                .thenReturn(List.of(summaryRow(1L, null, false)));

        NewsTodayResponse response = newsQueryService.getTodayNews(1L);

        assertThat(response.items())
                .extracting(NewsTodayResponse.NewsTodayItem::newsId)
                .containsExactly(1L);
    }

    @Test
    void getTodayNews_returnsOnlyLatestPublishedDateNewsWhenTodayNewsIsEmpty() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime latestPublishedCreatedAt = LocalDateTime.of(2026, 7, 29, 15, 30);
        LocalDateTime fallbackStart = LocalDateTime.of(2026, 7, 29, 0, 0);
        when(newsQueryRepository.findTodayPublishedNews(1L, todayStart, todayStart.plusDays(1), 3))
                .thenReturn(List.of());
        when(newsQueryRepository.findLatestPublishedCreatedAtBefore(todayStart))
                .thenReturn(Optional.of(latestPublishedCreatedAt));
        when(newsQueryRepository.findTodayPublishedNews(1L, fallbackStart, fallbackStart.plusDays(1), 3))
                .thenReturn(List.of(summaryRow(2L, null, false), summaryRow(1L, null, false)));

        NewsTodayResponse response = newsQueryService.getTodayNews(1L);

        assertThat(response.items())
                .extracting(NewsTodayResponse.NewsTodayItem::newsId)
                .containsExactly(2L, 1L);
    }

    @Test
    void getTodayNews_returnsEmptyItemsWhenPublishedNewsDoesNotExist() {
        LocalDateTime todayStart = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(newsQueryRepository.findTodayPublishedNews(1L, todayStart, todayStart.plusDays(1), 3))
                .thenReturn(List.of());
        when(newsQueryRepository.findLatestPublishedCreatedAtBefore(todayStart)).thenReturn(Optional.empty());

        NewsTodayResponse response = newsQueryService.getTodayNews(1L);

        assertThat(response.items()).isEmpty();
    }

    /** 목록 썸네일: 원본이 있으면 그 URL을, 없으면 null을 담는다(기본 이미지는 프론트가 처리). */
    @Test
    void getNewsList_returnsOriginalThumbnailOrNull() {
        when(newsQueryRepository.findPublishedNewsPage(anyLong(), any(), any(), anyInt()))
                .thenReturn(List.of(summaryRow(1L, null, true), summaryRow(2L, "https://origin/2.jpg", false)));

        NewsListResponse response = newsQueryService.getNewsList(1L, null, 10, null);

        assertThat(response.items())
                .extracting(NewsListResponse.NewsListItem::thumbnailUrl)
                .containsExactly(null, "https://origin/2.jpg");
        assertThat(response.items())
                .extracting(NewsListResponse.NewsListItem::isScrapped)
                .containsExactly(true, false);
    }

    /** 상세 썸네일: 원본이 없으면 thumbnailUrl은 null이다(기본 이미지는 프론트가 처리). */
    @Test
    void getNewsDetail_returnsNullThumbnailWhenMissing() {
        Long userId = 1L;
        Long newsId = 10L;
        News news = publishedNews(newsId);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(readHistoryRepository.findByUserIdAndNewsId(userId, newsId)).thenReturn(Optional.empty());
        when(scrapRepository.existsByUserIdAndNewsId(userId, newsId)).thenReturn(false);
        when(categoryRepository.findById(news.getCategoryId())).thenReturn(Optional.empty());

        assertThat(newsQueryService.getNewsDetail(userId, newsId).thumbnailUrl())
                .isNull();
    }

    private static NewsSummaryRow summaryRow(Long newsId, String thumbnailUrl, boolean isScrapped) {
        return new NewsSummaryRow(
                newsId,
                1L,
                "경제",
                "제목 " + newsId,
                "요약",
                "매일경제",
                LocalDateTime.of(2026, 7, 18, 9, 0),
                thumbnailUrl,
                0L,
                isScrapped);
    }
}
