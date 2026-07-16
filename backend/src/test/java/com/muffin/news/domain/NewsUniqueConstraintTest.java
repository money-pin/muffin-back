package com.muffin.news.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTerm;
import com.muffin.news.domain.term.UserSavedTermRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 뉴스 도메인의 주요 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsUniqueConstraintTest {

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TermDictionaryRepository termDictionaryRepository;

    @Autowired
    private UserSavedTermRepository userSavedTermRepository;

    @Test
    @DisplayName("같은 원문 URL로 뉴스를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void news_duplicateOriginalUrl_violatesUnique() {
        newsRepository.saveAndFlush(createNews("https://example.com/news/1"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> newsRepository.saveAndFlush(createNews("https://example.com/news/1")));
    }

    @Test
    @DisplayName("같은 카테고리 이름을 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void category_duplicateName_violatesUnique() {
        categoryRepository.saveAndFlush(Category.create("경제", null));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> categoryRepository.saveAndFlush(Category.create("경제", null)));
    }

    @Test
    @DisplayName("같은 용어를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void termDictionary_duplicateTerm_violatesUnique() {
        termDictionaryRepository.saveAndFlush(TermDictionary.create("기준금리", "설명 1"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> termDictionaryRepository.saveAndFlush(TermDictionary.create("기준금리", "설명 2")));
    }

    @Test
    @DisplayName("같은 사용자가 같은 용어를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void userSavedTerm_duplicateUserAndTerm_violatesUnique() {
        userSavedTermRepository.saveAndFlush(UserSavedTerm.create(1L, 10L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userSavedTermRepository.saveAndFlush(UserSavedTerm.create(1L, 10L)));
    }

    @Test
    @DisplayName("같은 뉴스에 같은 용어를 두 번 추가하면 UNIQUE 제약 위반이 발생한다")
    void newsTerm_duplicateNewsAndTerm_violatesUnique() {
        News news = createNews("https://example.com/news/2");
        news.addTerm(10L);
        news.addTerm(10L);

        assertThrows(DataIntegrityViolationException.class, () -> newsRepository.saveAndFlush(news));
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
