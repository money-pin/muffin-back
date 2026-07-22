package com.muffin.scrap.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.scrap.domain.Scrap;
import com.muffin.scrap.domain.ScrapRepository;
import com.muffin.scrap.presentation.dto.ScrapResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** 스크랩/해제의 멱등성과 뉴스 존재/공개 검증을 단위로 검증한다. 저장소는 Mockito mock으로 대체한다. */
class ScrapCommandServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long NEWS_ID = 1024L;
    private static final LocalDateTime FIRST_SCRAPPED_AT = LocalDateTime.of(2026, 5, 8, 14, 30, 0);

    private ScrapRepository scrapRepository;
    private ScrapWriter scrapWriter;
    private NewsRepository newsRepository;
    private ScrapCommandService scrapCommandService;

    @BeforeEach
    void setUp() {
        scrapRepository = mock(ScrapRepository.class);
        scrapWriter = mock(ScrapWriter.class);
        newsRepository = mock(NewsRepository.class);
        Clock clock = Clock.fixed(FIRST_SCRAPPED_AT.atZone(KST).toInstant(), KST);
        scrapCommandService = new ScrapCommandService(scrapRepository, scrapWriter, newsRepository, clock);
    }

    @Test
    @DisplayName("공개 뉴스를 처음 스크랩하면 새로 저장하고 스크랩 시각을 반환한다")
    void scrap_savesWhenAbsent() {
        givenPublishedNews();
        when(scrapRepository.findByUserIdAndNewsId(USER_ID, NEWS_ID)).thenReturn(Optional.empty());
        when(scrapWriter.insert(USER_ID, NEWS_ID)).thenReturn(scrapWithCreatedAt(FIRST_SCRAPPED_AT));

        ScrapResponse response = scrapCommandService.scrap(USER_ID, NEWS_ID);

        assertThat(response.newsId()).isEqualTo(NEWS_ID);
        assertThat(response.isScrapped()).isTrue();
        assertThat(response.scrappedAt()).isEqualTo(OffsetDateTime.parse("2026-05-08T14:30:00+09:00"));
        verify(scrapWriter).insert(USER_ID, NEWS_ID);
    }

    @Test
    @DisplayName("이미 스크랩한 뉴스를 다시 스크랩하면 최초 저장 시각을 유지하고 새로 저장하지 않는다")
    void scrap_keepsFirstScrappedAtWhenAlreadyScrapped() {
        givenPublishedNews();
        when(scrapRepository.findByUserIdAndNewsId(USER_ID, NEWS_ID))
                .thenReturn(Optional.of(scrapWithCreatedAt(FIRST_SCRAPPED_AT)));

        ScrapResponse response = scrapCommandService.scrap(USER_ID, NEWS_ID);

        assertThat(response.isScrapped()).isTrue();
        assertThat(response.scrappedAt()).isEqualTo(OffsetDateTime.parse("2026-05-08T14:30:00+09:00"));
        verify(scrapWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 뉴스를 스크랩하면 CONTENT_404_001 예외를 던진다")
    void scrap_throwsWhenNewsNotFound() {
        when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scrapCommandService.scrap(USER_ID, NEWS_ID))
                .isInstanceOf(NewsException.class)
                .extracting("errorCode")
                .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
        verify(scrapWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("아직 공개되지 않은 뉴스를 스크랩하면 CONTENT_403_001 예외를 던진다")
    void scrap_throwsWhenNewsNotPublished() {
        News news = mock(News.class);
        when(news.getDeletedAt()).thenReturn(null);
        when(news.getStatus()).thenReturn(NewsStatus.PROCESSING);
        when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.of(news));

        assertThatThrownBy(() -> scrapCommandService.scrap(USER_ID, NEWS_ID))
                .isInstanceOf(NewsException.class)
                .extracting("errorCode")
                .isEqualTo(NewsErrorCode.NEWS_NOT_PUBLISHED);
        verify(scrapWriter, never()).insert(any(), any());
    }

    @Test
    @DisplayName("동시 스크랩으로 삽입이 유니크 제약에 걸리면 이미 저장된 행을 재조회해 반환한다")
    void scrap_recoversExistingOnConcurrentInsert() {
        givenPublishedNews();
        // 첫 조회는 없음(삽입 시도) → 삽입이 동시 삽입과 충돌 → 두 번째 조회에서 이미 저장된 행 발견
        when(scrapRepository.findByUserIdAndNewsId(USER_ID, NEWS_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(scrapWithCreatedAt(FIRST_SCRAPPED_AT)));
        when(scrapWriter.insert(USER_ID, NEWS_ID)).thenThrow(new DataIntegrityViolationException("duplicate"));

        ScrapResponse response = scrapCommandService.scrap(USER_ID, NEWS_ID);

        assertThat(response.isScrapped()).isTrue();
        assertThat(response.scrappedAt()).isEqualTo(OffsetDateTime.parse("2026-05-08T14:30:00+09:00"));
    }

    @Test
    @DisplayName("삽입 충돌 후에도 행을 찾지 못하면 원래 예외를 다시 던진다")
    void scrap_rethrowsWhenRecoveryFindsNothing() {
        givenPublishedNews();
        when(scrapRepository.findByUserIdAndNewsId(USER_ID, NEWS_ID)).thenReturn(Optional.empty());
        when(scrapWriter.insert(USER_ID, NEWS_ID)).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> scrapCommandService.scrap(USER_ID, NEWS_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("스크랩을 해제하면 삭제하고 isScrapped=false 를 반환한다(scrappedAt 생략)")
    void unscrap_deletesAndReturnsFalse() {
        givenPublishedNews();

        ScrapResponse response = scrapCommandService.unscrap(USER_ID, NEWS_ID);

        assertThat(response.newsId()).isEqualTo(NEWS_ID);
        assertThat(response.isScrapped()).isFalse();
        assertThat(response.scrappedAt()).isNull();
        verify(scrapRepository).deleteByUserIdAndNewsId(USER_ID, NEWS_ID);
    }

    @Test
    @DisplayName("스크랩하지 않은 뉴스를 해제해도 성공한다(멱등)")
    void unscrap_isIdempotentWhenAbsent() {
        givenPublishedNews();

        ScrapResponse response = scrapCommandService.unscrap(USER_ID, NEWS_ID);

        assertThat(response.isScrapped()).isFalse();
        verify(scrapRepository).deleteByUserIdAndNewsId(USER_ID, NEWS_ID);
    }

    @Test
    @DisplayName("존재하지 않는 뉴스를 해제하면 CONTENT_404_001 예외를 던진다")
    void unscrap_throwsWhenNewsNotFound() {
        when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scrapCommandService.unscrap(USER_ID, NEWS_ID))
                .isInstanceOf(NewsException.class)
                .extracting("errorCode")
                .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
        verify(scrapRepository, never()).deleteByUserIdAndNewsId(USER_ID, NEWS_ID);
    }

    private void givenPublishedNews() {
        News news = mock(News.class);
        when(news.getDeletedAt()).thenReturn(null);
        when(news.getStatus()).thenReturn(NewsStatus.PUBLISHED);
        when(newsRepository.findById(NEWS_ID)).thenReturn(Optional.of(news));
    }

    private Scrap scrapWithCreatedAt(LocalDateTime createdAt) {
        Scrap scrap = Scrap.create(USER_ID, NEWS_ID);
        ReflectionTestUtils.setField(scrap, "createdAt", createdAt);
        return scrap;
    }
}
