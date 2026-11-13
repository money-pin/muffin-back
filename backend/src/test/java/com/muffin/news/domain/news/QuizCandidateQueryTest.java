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
 * 퀴즈 출제 후보를 기사 작성 시각이 아니라 수집 시각으로 고르는지 검증한다.
 *
 * <p>작성 시각으로 자르면 당일 수집분 중 어제 쓰인 기사가 통째로 빠진다. 그 기사들은 어제 퀴즈가 돌 때는 아직 수집 전이었으므로
 * 어느 날 퀴즈에도 쓰이지 못한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizCandidateQueryTest {

    private static final LocalDate QUIZ_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDateTime TODAY_COLLECTION = QUIZ_DATE.atTime(6, 5);
    private static final LocalDateTime YESTERDAY_COLLECTION =
            QUIZ_DATE.minusDays(1).atTime(6, 5);

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private EntityManager entityManager;

    /** 피드에서 00시~06시에 쓰인 기사는 2% 남짓이라, 작성 시각으로 거르면 후보가 출제에 필요한 3건에 못 미친다. */
    @Test
    @DisplayName("어제 쓰였어도 오늘 수집된 기사면 퀴즈 후보가 된다")
    void findReconstructedQuizCandidates_includesArticleWrittenYesterdayButCollectedToday() {
        Long writtenYesterday = savePendingNews(
                "https://example.com/written-yesterday", QUIZ_DATE.minusDays(1).atTime(14, 0), TODAY_COLLECTION);
        Long writtenOvernight =
                savePendingNews("https://example.com/written-overnight", QUIZ_DATE.atTime(3, 0), TODAY_COLLECTION);

        List<News> candidates = findReconstructedCandidates();

        assertThat(candidates).extracting(News::getId).containsExactly(writtenOvernight, writtenYesterday);
    }

    /** 어제 수집분은 어제 퀴즈가 이미 썼다. 오늘 다시 출제되면 같은 기사로 두 번 묻게 된다. */
    @Test
    @DisplayName("작성 시각이 오늘이어도 어제 수집분은 후보에서 빠진다")
    void findReconstructedQuizCandidates_excludesYesterdayCollection() {
        savePendingNews("https://example.com/collected-yesterday", QUIZ_DATE.atTime(2, 0), YESTERDAY_COLLECTION);

        assertThat(findReconstructedCandidates()).isEmpty();
    }

    private List<News> findReconstructedCandidates() {
        return newsRepository.findReconstructedQuizCandidates(
                NewsStatus.PENDING,
                QUIZ_DATE.atStartOfDay(),
                QUIZ_DATE.plusDays(1).atStartOfDay(),
                PageRequest.of(0, 15));
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
