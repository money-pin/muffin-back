package com.muffin.news.domain.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.news.domain.news.enums.NewsStatus;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 브리핑 후보를 기사 작성 시각이 아니라 수집 시각으로 고르는지 검증한다.
 *
 * <p>RSS 피드 한 번에 어제 쓰인 기사와 오늘 새벽에 쓰인 기사가 섞여 들어오기 때문에 둘을 구분해야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BriefingCandidateQueryTest {

    private static final LocalDate BRIEFING_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDateTime TODAY_COLLECTION = BRIEFING_DATE.atTime(6, 5);
    private static final LocalDateTime YESTERDAY_COLLECTION =
            BRIEFING_DATE.minusDays(1).atTime(6, 5);

    private static final List<NewsStatus> CANDIDATE_STATUSES = List.of(NewsStatus.PENDING, NewsStatus.PUBLISHED);

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 두 기사의 작성 시각과 수집 시각을 일부러 엇갈리게 둔다. 작성 시각으로 자르던 종전 쿼리는 정확히 반대 결과를 낸다.
     */
    @Test
    @DisplayName("오늘 수집된 기사만 후보가 되고, 작성 시각이 오늘이어도 어제 수집분은 빠진다")
    void findBriefingCandidates_filtersByCollectionTimeNotArticleTime() {
        Long collectedYesterday = savePendingNews(
                "https://example.com/collected-yesterday", BRIEFING_DATE.atTime(2, 0), YESTERDAY_COLLECTION);
        Long collectedToday = savePendingNews(
                "https://example.com/collected-today",
                BRIEFING_DATE.minusDays(1).atTime(20, 0),
                TODAY_COLLECTION);

        List<News> candidates = findCandidates();

        assertThat(candidates).extracting(News::getId).containsExactly(collectedToday);
        assertThat(candidates).extracting(News::getId).doesNotContain(collectedYesterday);
    }

    /** 같은 수집분 안에서는 최근에 쓰인 기사가 먼저 와야 한다. */
    @Test
    @DisplayName("같은 수집분은 기사 작성 시각 내림차순으로 정렬된다")
    void findBriefingCandidates_ordersByArticleTimeWithinSameCollection() {
        Long older = savePendingNews(
                "https://example.com/older", BRIEFING_DATE.minusDays(1).atTime(9, 0), TODAY_COLLECTION);
        Long newer = savePendingNews("https://example.com/newer", BRIEFING_DATE.atTime(5, 0), TODAY_COLLECTION);

        assertThat(findCandidates()).extracting(News::getId).containsExactly(newer, older);
    }

    /** 재구성이 끝나지 않은 기사는 본문이 없어 이슈로 쓸 수 없다. */
    @Test
    @DisplayName("재구성 전 기사는 같은 날 수집분이어도 후보에서 빠진다")
    void findBriefingCandidates_excludesNewsWithoutReconstruction() {
        News news = News.processing(
                1L, "재구성 전 뉴스", "매일경제", BRIEFING_DATE.atTime(5, 0), null, "https://example.com/not-reconstructed");
        Long newsId = newsRepository.saveAndFlush(news).getId();
        forceCollectedAt(newsId, TODAY_COLLECTION);

        assertThat(findCandidates()).isEmpty();
    }

    private List<News> findCandidates() {
        return newsRepository.findBriefingCandidates(
                CANDIDATE_STATUSES,
                BRIEFING_DATE.atStartOfDay(),
                BRIEFING_DATE.plusDays(1).atStartOfDay(),
                PageRequest.of(0, 40));
    }

    private Long savePendingNews(String originalUrl, LocalDateTime publishedAt, LocalDateTime collectedAt) {
        News news = News.processing(1L, "뉴스", "매일경제", publishedAt, null, originalUrl);
        news.completeReconstruction("뉴스", "뉴스 요약", "재구성된 본문입니다.");
        Long newsId = newsRepository.saveAndFlush(news).getId();
        forceCollectedAt(newsId, collectedAt);
        return newsId;
    }

    /** created_at은 저장 시점에 감사 기능이 채우므로, 수집 시각을 흉내 내려면 저장 후에 덮어쓴다. */
    private void forceCollectedAt(Long newsId, LocalDateTime collectedAt) {
        entityManager
                .createNativeQuery("UPDATE news SET created_at = :collectedAt WHERE news_id = :newsId")
                .setParameter("collectedAt", collectedAt)
                .setParameter("newsId", newsId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
