package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class NewsTermMappingServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final NewsTermMappingService mappingService =
            new NewsTermMappingService(newsRepository, termDictionaryRepository);

    @Test
    void mapTerms_addsMatchedTermsToNews() {
        Long newsId = 1L;
        News news = reconstructedNews(newsId, "한국은행이 기준금리를 올리면서 통화정책을 긴축 방향으로 전환했습니다.");
        TermDictionary baseRate = term(10L, "기준금리");
        TermDictionary monetaryPolicy = term(20L, "통화정책");
        TermDictionary cofix = term(30L, "코픽스");
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(termDictionaryRepository.findAll()).thenReturn(List.of(baseRate, monetaryPolicy, cofix));

        int mappedCount = mappingService.mapTerms(newsId);

        assertThat(mappedCount).isEqualTo(2);
        assertThat(news.getTerms()).extracting("termId").containsExactlyInAnyOrder(10L, 20L);
        verify(newsRepository).save(news);
    }

    @Test
    void mapTerms_skipsAlreadyMappedTerms() {
        Long newsId = 1L;
        News news = reconstructedNews(newsId, "기준금리가 다시 뉴스에 등장했습니다.");
        news.addTerm(10L);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(termDictionaryRepository.findAll()).thenReturn(List.of(term(10L, "기준금리")));

        int mappedCount = mappingService.mapTerms(newsId);

        assertThat(mappedCount).isZero();
        assertThat(news.getTerms()).hasSize(1);
        verify(newsRepository, never()).save(news);
    }

    @Test
    void mapTerms_skipsWhenReconstructionResultIsMissing() {
        Long newsId = 1L;
        News news = News.processing(1L, "뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://example.com");
        ReflectionTestUtils.setField(news, "id", newsId);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));

        int mappedCount = mappingService.mapTerms(newsId);

        assertThat(mappedCount).isZero();
        verify(termDictionaryRepository, never()).findAll();
        verify(newsRepository, never()).save(news);
    }

    private static News reconstructedNews(Long newsId, String content) {
        News news = News.processing(1L, "뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://example.com");
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction("뉴스 요약", content);
        return news;
    }

    private static TermDictionary term(Long termId, String term) {
        TermDictionary dictionary = TermDictionary.create(term, term + " 설명");
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }
}
