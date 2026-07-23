package com.muffin.mypage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.mypage.application.RecentNewsCursor;
import com.muffin.mypage.application.RecentNewsQueryRepository;
import com.muffin.mypage.application.projection.RecentNewsProjection;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/** 최근 읽은 뉴스 쿼리가 사용자/미삭제 뉴스로 필터링하고 열람 시각 내림차순 커서 페이지네이션으로 조회하는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, RecentNewsQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecentNewsQueryRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private RecentNewsQueryRepository recentNewsQueryRepository;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ReadHistoryRepository readHistoryRepository;

    private Long newsAId;
    private Long newsBId;
    private Long newsCId;

    @BeforeEach
    void setUp() {
        Long categoryId = categoryRepository
                .save(Category.create("반도체", "https://fallback.png"))
                .getId();

        newsAId = savePublished(categoryId, "뉴스A", LocalDateTime.of(2026, 5, 7, 9, 0), 10L);
        newsBId = savePublished(categoryId, "뉴스B", LocalDateTime.of(2026, 5, 6, 9, 0), 30L);
        newsCId = savePublished(categoryId, "뉴스C", LocalDateTime.of(2026, 5, 5, 9, 0), 20L);
        Long deletedNewsId = savePublished(categoryId, "삭제뉴스", LocalDateTime.of(2026, 5, 8, 9, 0), 99L);
        softDelete(deletedNewsId);
        savePublished(categoryId, "안읽음", LocalDateTime.of(2026, 5, 4, 9, 0), 5L);

        // 열람 시각: A(20:00) < C(21:00) < B(22:00). 열람 시각 내림차순은 B, C, A.
        saveRead(USER_ID, newsAId, LocalDateTime.of(2026, 5, 8, 20, 0));
        saveRead(USER_ID, newsCId, LocalDateTime.of(2026, 5, 8, 21, 0));
        saveRead(USER_ID, newsBId, LocalDateTime.of(2026, 5, 8, 22, 0));
        saveRead(USER_ID, deletedNewsId, LocalDateTime.of(2026, 5, 8, 23, 0)); // 삭제 뉴스 → 제외
        saveRead(OTHER_USER_ID, newsAId, LocalDateTime.of(2026, 5, 8, 23, 30)); // 다른 사용자 → 제외
    }

    @Test
    @DisplayName("본인 열람 기록만 열람 시각 내림차순으로 조회하고 삭제 뉴스/타인 기록은 제외한다")
    void findRecentNewsPage_ordersByViewedAtDesc() {
        List<RecentNewsProjection> projections = recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 10);

        assertThat(projections).extracting(RecentNewsProjection::newsId).containsExactly(newsBId, newsCId, newsAId);
    }

    @Test
    @DisplayName("커서 이후 행만 조회한다(2페이지)")
    void findRecentNewsPage_appliesCursor() {
        List<RecentNewsProjection> firstPage = recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 2);
        assertThat(firstPage).extracting(RecentNewsProjection::newsId).containsExactly(newsBId, newsCId);

        RecentNewsProjection last = firstPage.get(firstPage.size() - 1);
        RecentNewsCursor cursor = new RecentNewsCursor(last.viewedAt(), last.readHistoryId());

        List<RecentNewsProjection> secondPage = recentNewsQueryRepository.findRecentNewsPage(USER_ID, cursor, 2);
        assertThat(secondPage).extracting(RecentNewsProjection::newsId).containsExactly(newsAId);
    }

    @Test
    @DisplayName("조인한 뉴스/카테고리 필드를 projection으로 채운다")
    void findRecentNewsPage_projectsJoinedFields() {
        List<RecentNewsProjection> projections = recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 1);

        RecentNewsProjection projection = projections.getFirst();
        assertThat(projection.newsId()).isEqualTo(newsBId);
        assertThat(projection.title()).isEqualTo("뉴스B");
        assertThat(projection.categoryName()).isEqualTo("반도체");
        assertThat(projection.viewCount()).isEqualTo(30L);
        assertThat(projection.publishedAt()).isEqualTo(LocalDateTime.of(2026, 5, 6, 9, 0));
        assertThat(projection.viewedAt()).isEqualTo(LocalDateTime.of(2026, 5, 8, 22, 0));
        assertThat(projection.readHistoryId()).isNotNull();
    }

    private Long savePublished(Long categoryId, String title, LocalDateTime publishedAt, long viewCount) {
        News news = News.processing(
                categoryId, title, "매일경제", publishedAt, "https://thumb/" + title, "https://news/" + title);
        news.completeReconstruction(title + " 요약", title + " 재구성 본문");
        news.publish();
        ReflectionTestUtils.setField(news, "viewCount", viewCount);
        return newsRepository.save(news).getId();
    }

    private void saveRead(Long userId, Long newsId, LocalDateTime readAt) {
        ReadHistory readHistory = ReadHistory.create(userId, newsId);
        ReflectionTestUtils.setField(readHistory, "readAt", readAt);
        readHistoryRepository.save(readHistory);
    }

    private void softDelete(Long newsId) {
        News news = newsRepository.findById(newsId).orElseThrow();
        ReflectionTestUtils.setField(news, "deletedAt", LocalDateTime.now());
        newsRepository.save(news);
    }
}
