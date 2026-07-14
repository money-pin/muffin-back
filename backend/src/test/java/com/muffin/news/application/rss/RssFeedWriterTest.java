package com.muffin.news.application.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RssFeedWriterTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final RssFeedWriter writer = new RssFeedWriter(categoryRepository, newsRepository);

    /** 이미 존재하는 URL의 기사는 건너뛰고, 신규 URL의 기사만 PENDING 상태로 저장한다. */
    @Test
    void savesOnlyArticlesWithNewUrlsAsPending() {
        Category category = Category.create("경제", null);
        ReflectionTestUtils.setField(category, "id", 3L);
        RssArticle existing = article("기존", "https://example.com/1");
        RssArticle fresh = article("신규", "https://example.com/2");
        when(categoryRepository.findByName("경제")).thenReturn(Optional.of(category));
        when(newsRepository.existsByOriginalUrl(existing.url())).thenReturn(true);
        when(newsRepository.existsByOriginalUrl(fresh.url())).thenReturn(false);
        when(newsRepository.save(any(News.class))).thenAnswer(invocation -> {
            News news = invocation.getArgument(0);
            ReflectionTestUtils.setField(news, "id", 7L);
            return news;
        });

        List<Long> result = writer.save("경제", "매일경제", List.of(existing, fresh));

        assertThat(result).containsExactly(7L);
        ArgumentCaptor<News> newsCaptor = ArgumentCaptor.forClass(News.class);
        verify(newsRepository).save(newsCaptor.capture());
        assertThat(newsCaptor.getValue().getCategoryId()).isEqualTo(3L);
        assertThat(newsCaptor.getValue().getOriginalUrl()).isEqualTo(fresh.url());
        assertThat(newsCaptor.getValue().getContent()).isNull();
        assertThat(newsCaptor.getValue().getStatus().name()).isEqualTo("PENDING");
    }

    private static RssArticle article(String title, String url) {
        return new RssArticle(title, url, "요약", LocalDateTime.of(2026, 7, 12, 6, 0));
    }
}
