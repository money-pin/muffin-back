package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.application.term.NewsTermRemappingService.NewsTermRemappingReport;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.NewsTerm;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** 매칭 규칙이 바뀐 뒤 이미 저장된 연결을 바로잡는지 검증한다. */
class NewsTermRemappingServiceTest {

    private static final Long GIRO_ID = 10L;
    private static final Long BASE_RATE_ID = 20L;

    private NewsRepository newsRepository;
    private TermDictionaryRepository termDictionaryRepository;
    private NewsTermRemappingService remappingService;

    @BeforeEach
    void setUp() {
        newsRepository = mock(NewsRepository.class);
        termDictionaryRepository = mock(TermDictionaryRepository.class);

        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        remappingService = new NewsTermRemappingService(
                newsRepository, termDictionaryRepository, new TransactionTemplate(transactionManager));
    }

    /** 바뀐 규칙에서는 더 이상 걸리지 않는 연결을 지운다. 이 행은 하이라이트 말고 오늘의 용어 후보도 먹인다. */
    @Test
    void remapAll_removesLinksThatNoLongerMatch() {
        News news = reconstructedNews(1L, "관련 광고는 규정상 금지로 집행하지 않았습니다.");
        news.addTerm(GIRO_ID);
        givenDictionaryAndNews(news);

        NewsTermRemappingReport report = remappingService.remapAll();

        assertThat(news.getTerms()).isEmpty();
        assertThat(report.scannedNews()).isEqualTo(1);
        assertThat(report.changedNews()).isEqualTo(1);
        assertThat(report.changedLinks()).isEqualTo(1);
        verify(newsRepository).save(news);
    }

    /** 규칙이 바뀌어도 그대로 걸리는 연결은 건드리지 않는다. 불필요한 저장은 생성 시각만 흔든다. */
    @Test
    void remapAll_keepsLinksThatStillMatch() {
        News news = reconstructedNews(1L, "지로 용지로 기준금리 관련 비용을 납부했습니다.");
        news.addTerm(GIRO_ID);
        news.addTerm(BASE_RATE_ID);
        givenDictionaryAndNews(news);

        NewsTermRemappingReport report = remappingService.remapAll();

        assertThat(news.getTerms()).extracting(NewsTerm::getTermId).containsExactlyInAnyOrder(GIRO_ID, BASE_RATE_ID);
        assertThat(report.changedNews()).isZero();
        assertThat(report.changedLinks()).isZero();
        verify(newsRepository, never()).save(news);
    }

    /** 규칙 변경과 무관하게 애초에 빠져 있던 연결도 이 기회에 채운다. */
    @Test
    void remapAll_addsMissingLinks() {
        News news = reconstructedNews(1L, "한국은행이 기준금리를 올렸습니다.");
        givenDictionaryAndNews(news);

        NewsTermRemappingReport report = remappingService.remapAll();

        assertThat(news.getTerms()).extracting(NewsTerm::getTermId).containsExactly(BASE_RATE_ID);
        assertThat(report.changedLinks()).isEqualTo(1);
        verify(newsRepository).save(news);
    }

    /** 대상 뉴스가 없으면 용어 사전만 읽고 끝낸다. */
    @Test
    void remapAll_reportsNothingWhenThereIsNoNews() {
        when(termDictionaryRepository.findAll()).thenReturn(dictionary());
        when(newsRepository.findIdsForTermRemapping()).thenReturn(List.of());

        NewsTermRemappingReport report = remappingService.remapAll();

        assertThat(report.scannedNews()).isZero();
        verify(newsRepository, never()).findAllById(any());
    }

    /** 뉴스가 여러 건이어도 용어 사전은 한 번만 읽는다. 뉴스마다 다시 읽으면 질의 수에 비례해 느려진다. */
    @Test
    void remapAll_readsDictionaryOnlyOnce() {
        News first = reconstructedNews(1L, "지로 용지로 납부했습니다.");
        News second = reconstructedNews(2L, "기준금리를 올렸습니다.");
        when(termDictionaryRepository.findAll()).thenReturn(dictionary());
        when(newsRepository.findIdsForTermRemapping()).thenReturn(List.of(1L, 2L));
        when(newsRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));

        remappingService.remapAll();

        verify(termDictionaryRepository).findAll();
    }

    private void givenDictionaryAndNews(News news) {
        when(termDictionaryRepository.findAll()).thenReturn(dictionary());
        when(newsRepository.findIdsForTermRemapping()).thenReturn(List.of(news.getId()));
        when(newsRepository.findAllById(List.of(news.getId()))).thenReturn(List.of(news));
    }

    private static List<TermDictionary> dictionary() {
        return List.of(term(GIRO_ID, "지로(GIRO)"), term(BASE_RATE_ID, "기준금리"));
    }

    private static News reconstructedNews(Long newsId, String content) {
        News news = News.processing(1L, "뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://example.com");
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction("뉴스", "뉴스 요약", content);
        return news;
    }

    private static TermDictionary term(Long termId, String term) {
        TermDictionary dictionary = TermDictionary.create(term, term + " 설명");
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }
}
