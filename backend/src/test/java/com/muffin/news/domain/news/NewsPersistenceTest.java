package com.muffin.news.domain.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.muffin.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** News 애그리거트가 내부 엔티티인 NewsTerm까지 함께 저장되는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsPersistenceTest {

    @Autowired
    private NewsRepository newsRepository;

    @Test
    @DisplayName("용어를 포함한 뉴스를 저장하면 NewsTerm까지 함께 영속화된다")
    void saveNewsWithTerms_persistsChildren() {
        News news = createNews("https://example.com/news/1");
        news.addTerm(10L);
        news.addTerm(20L);

        News saved = newsRepository.saveAndFlush(news);

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(2, saved.getTerms().size());
    }

    private News createNews(String originalUrl) {
        return News.processing(
                1L,
                "경제 뉴스",
                "muffin",
                LocalDateTime.of(2026, 7, 7, 9, 0),
                "https://example.com/thumb.png",
                originalUrl);
    }
}
