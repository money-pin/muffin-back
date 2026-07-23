package com.muffin.mypage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.mypage.application.ScrapCursor;
import com.muffin.mypage.application.ScrapListQueryRepository;
import com.muffin.mypage.application.ScrapListRow;
import com.muffin.mypage.domain.ScrapSort;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
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

/** 스크랩 목록 쿼리가 사용자/미삭제 뉴스로 필터링하고 정렬별 커서 페이지네이션으로 조회하는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, ScrapListQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ScrapListQueryRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;

    @Autowired
    private ScrapListQueryRepository scrapListQueryRepository;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ScrapRepository scrapRepository;

    private Long newsAId;
    private Long newsBId;
    private Long newsCId;

    @BeforeEach
    void setUp() {
        Long categoryId = categoryRepository
                .save(Category.create("반도체", "https://fallback.png"))
                .getId();

        // 정렬 3종을 서로 다른 순서로 구분하기 위해 발행일/조회수를 교차 배치한다.
        newsAId = savePublished(categoryId, "뉴스A", LocalDateTime.of(2026, 5, 7, 9, 0), 10L);
        newsBId = savePublished(categoryId, "뉴스B", LocalDateTime.of(2026, 5, 6, 9, 0), 30L);
        newsCId = savePublished(categoryId, "뉴스C", LocalDateTime.of(2026, 5, 5, 9, 0), 20L);
        Long deletedNewsId = savePublished(categoryId, "삭제뉴스", LocalDateTime.of(2026, 5, 8, 9, 0), 99L);
        softDelete(deletedNewsId);
        savePublished(categoryId, "스크랩안함", LocalDateTime.of(2026, 5, 4, 9, 0), 5L);

        // 저장 순서 A → B → C (scrap.id 오름차순). 저장순 정렬은 id 내림차순으로 C, B, A.
        scrapRepository.save(Scrap.create(USER_ID, newsAId));
        scrapRepository.save(Scrap.create(USER_ID, newsBId));
        scrapRepository.save(Scrap.create(USER_ID, newsCId));
        scrapRepository.save(Scrap.create(USER_ID, deletedNewsId)); // 삭제 뉴스 → 제외
        scrapRepository.save(Scrap.create(OTHER_USER_ID, newsAId)); // 다른 사용자 → 제외
    }

    @Test
    @DisplayName("SAVED_DESC는 본인 스크랩만 최근 저장순으로 조회하고 삭제 뉴스/타인 스크랩은 제외한다")
    void findScrapPage_savedDesc() {
        List<ScrapListRow> rows = scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 10);

        assertThat(rows).extracting(ScrapListRow::newsId).containsExactly(newsCId, newsBId, newsAId);
    }

    @Test
    @DisplayName("PUBLISHED_DESC는 발행일 내림차순으로 조회한다")
    void findScrapPage_publishedDesc() {
        List<ScrapListRow> rows = scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.PUBLISHED_DESC, null, 10);

        assertThat(rows).extracting(ScrapListRow::newsId).containsExactly(newsAId, newsBId, newsCId);
    }

    @Test
    @DisplayName("VIEW_DESC는 조회수 내림차순으로 조회한다")
    void findScrapPage_viewDesc() {
        List<ScrapListRow> rows = scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.VIEW_DESC, null, 10);

        assertThat(rows).extracting(ScrapListRow::newsId).containsExactly(newsBId, newsCId, newsAId);
    }

    @Test
    @DisplayName("커서 이후 행만 조회한다(SAVED_DESC 2페이지)")
    void findScrapPage_appliesCursor() {
        List<ScrapListRow> firstPage = scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 2);
        assertThat(firstPage).extracting(ScrapListRow::newsId).containsExactly(newsCId, newsBId);

        ScrapListRow last = firstPage.get(firstPage.size() - 1);
        ScrapCursor cursor = new ScrapCursor(ScrapSort.SAVED_DESC, last.scrappedAt(), null, last.scrapId());

        List<ScrapListRow> secondPage =
                scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, cursor, 2);
        assertThat(secondPage).extracting(ScrapListRow::newsId).containsExactly(newsAId);
    }

    @Test
    @DisplayName("조인한 뉴스/카테고리 필드를 projection으로 채운다")
    void findScrapPage_projectsJoinedFields() {
        List<ScrapListRow> rows = scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.PUBLISHED_DESC, null, 1);

        ScrapListRow row = rows.getFirst();
        assertThat(row.newsId()).isEqualTo(newsAId);
        assertThat(row.title()).isEqualTo("뉴스A");
        assertThat(row.categoryName()).isEqualTo("반도체");
        assertThat(row.viewCount()).isEqualTo(10L);
        assertThat(row.publishedAt()).isEqualTo(LocalDateTime.of(2026, 5, 7, 9, 0));
        assertThat(row.scrappedAt()).isNotNull();
        assertThat(row.scrapId()).isNotNull();
    }

    private Long savePublished(Long categoryId, String title, LocalDateTime publishedAt, long viewCount) {
        News news = News.processing(
                categoryId, title, "매일경제", publishedAt, "https://thumb/" + title, "https://news/" + title);
        news.completeReconstruction(title + " 요약", title + " 재구성 본문");
        news.publish();
        ReflectionTestUtils.setField(news, "viewCount", viewCount);
        return newsRepository.save(news).getId();
    }

    private void softDelete(Long newsId) {
        News news = newsRepository.findById(newsId).orElseThrow();
        ReflectionTestUtils.setField(news, "deletedAt", LocalDateTime.now());
        newsRepository.save(news);
    }
}
