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
import com.muffin.scrap.domain.Scrap;
import com.muffin.scrap.domain.ScrapRepository;
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

    @Autowired
    private ScrapRepository scrapRepository;

    private Long categoryId;
    private Long newsAId;
    private Long newsBId;
    private Long newsCId;

    @BeforeEach
    void setUp() {
        categoryId = categoryRepository
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
    @DisplayName("열람 시각이 같으면 read_history.id 내림차순으로 정렬하고 커서 타이브레이크가 동작한다")
    void findRecentNewsPage_breaksTieByReadHistoryIdDesc() {
        // 기존 최근 열람(최대 05-08 22:00)보다 뒤인 같은 시각으로 두 건을 열람: id로만 순서가 갈린다.
        LocalDateTime sameReadAt = LocalDateTime.of(2026, 5, 9, 10, 0);
        Long newsDId = savePublished(categoryId, "뉴스D", LocalDateTime.of(2026, 5, 3, 9, 0), 1L);
        Long newsEId = savePublished(categoryId, "뉴스E", LocalDateTime.of(2026, 5, 2, 9, 0), 1L);
        saveRead(USER_ID, newsDId, sameReadAt); // 먼저 저장 → 작은 read_history.id
        saveRead(USER_ID, newsEId, sameReadAt); // 나중 저장 → 큰 read_history.id

        List<RecentNewsProjection> firstPage = recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 2);
        // 같은 열람 시각이면 id 내림차순 → 나중 저장한 E가 먼저.
        assertThat(firstPage).extracting(RecentNewsProjection::newsId).containsExactly(newsEId, newsDId);

        // E(큰 id) 커서 다음은 같은 시각의 D(작은 id)여야 한다(타이브레이크 predicate 검증).
        RecentNewsProjection first = firstPage.getFirst();
        RecentNewsCursor cursor = new RecentNewsCursor(first.viewedAt(), first.readHistoryId());
        List<RecentNewsProjection> nextPage = recentNewsQueryRepository.findRecentNewsPage(USER_ID, cursor, 1);
        assertThat(nextPage).extracting(RecentNewsProjection::newsId).containsExactly(newsDId);
    }

    @Test
    @DisplayName("조인한 뉴스/카테고리 필드를 projection으로 채운다")
    void findRecentNewsPage_projectsJoinedFields() {
        scrapRepository.save(Scrap.create(USER_ID, newsBId));
        scrapRepository.save(Scrap.create(OTHER_USER_ID, newsAId));

        List<RecentNewsProjection> projections = recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 10);

        RecentNewsProjection projection = projections.getFirst();
        assertThat(projection.newsId()).isEqualTo(newsBId);
        assertThat(projection.title()).isEqualTo("뉴스B");
        assertThat(projection.categoryName()).isEqualTo("반도체");
        assertThat(projection.viewCount()).isEqualTo(30L);
        assertThat(projection.publishedAt()).isEqualTo(LocalDateTime.of(2026, 5, 6, 9, 0));
        assertThat(projection.viewedAt()).isEqualTo(LocalDateTime.of(2026, 5, 8, 22, 0));
        assertThat(projections).extracting(RecentNewsProjection::isScrapped).containsExactly(true, false, false);
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
