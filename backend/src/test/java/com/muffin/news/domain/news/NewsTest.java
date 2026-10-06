package com.muffin.news.domain.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.news.domain.news.enums.NewsStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsTest {

    private static final Long CATEGORY_ID = 1L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 7, 9, 0);

    @Test
    @DisplayName("RSS 뉴스를 생성하면 처리 중 상태와 조회수 0으로 초기화된다")
    void processing_initializesProcessingStatusAndZeroViewCount() {
        News news = createNews();

        assertEquals(NewsStatus.PROCESSING, news.getStatus());
        assertEquals(0L, news.getViewCount());
    }

    @Test
    @DisplayName("AI 재구성이 완료되면 결과를 저장하고 발행 대기 상태로 변경된다")
    void completeReconstruction_setsPendingStatusAndContent() {
        News news = createNews();

        news.completeReconstruction("AI가 쓴 제목", "한 줄 요약", "재구성된 뉴스 본문");

        assertEquals("한 줄 요약", news.getSummary());
        assertEquals("재구성된 뉴스 본문", news.getContent());
        assertEquals(NewsStatus.PENDING, news.getStatus());
    }

    @Test
    @DisplayName("AI 제목으로 교체해도 매경 원문 제목은 그대로 남는다")
    void completeReconstruction_replacesTitleButKeepsOriginalTitle() {
        News news = createNews();

        news.completeReconstruction("반도체 수출 3개월째 증가", "한 줄 요약", "재구성된 뉴스 본문");

        assertEquals("반도체 수출 3개월째 증가", news.getTitle());
        assertEquals("경제 뉴스", news.getOriginalTitle());
    }

    @Test
    @DisplayName("AI 제목 앞뒤 공백은 제거하고 저장한다")
    void completeReconstruction_stripsTitle() {
        News news = createNews();

        news.completeReconstruction("  반도체 수출 증가  ", "한 줄 요약", "재구성된 뉴스 본문");

        assertEquals("반도체 수출 증가", news.getTitle());
    }

    /** 제목 하나 때문에 뉴스를 실패시키면 그날 해설카드·퀴즈까지 연쇄로 비므로, 원문 제목을 유지하고 재구성은 완료시킨다. */
    @Test
    @DisplayName("AI 제목이 비었거나 30자를 넘거나 줄바꿈이 섞이면 원문 제목을 유지하고 재구성은 완료된다")
    void completeReconstruction_keepsOriginalTitleWhenAiTitleIsUnusable() {
        String tooLong = "가".repeat(31);

        for (String unusable : new String[] {null, "", "   ", tooLong, "제목\n두 번째 줄", "제목\r두 번째 줄"}) {
            News news = createNews();

            news.completeReconstruction(unusable, "한 줄 요약", "재구성된 뉴스 본문");

            assertEquals("경제 뉴스", news.getTitle());
            assertEquals("경제 뉴스", news.getOriginalTitle());
            assertEquals(NewsStatus.PENDING, news.getStatus());
        }
    }

    @Test
    @DisplayName("제목이 정확히 30자면 그대로 사용한다")
    void completeReconstruction_acceptsTitleAtMaxLength() {
        News news = createNews();
        String maxLength = "가".repeat(30);

        news.completeReconstruction(maxLength, "한 줄 요약", "재구성된 뉴스 본문");

        assertEquals(maxLength, news.getTitle());
    }

    @Test
    @DisplayName("RSS로 수집한 시점에는 제목과 원문 제목이 같다")
    void processing_setsOriginalTitleToCollectedTitle() {
        News news = createNews();

        assertEquals("경제 뉴스", news.getTitle());
        assertEquals("경제 뉴스", news.getOriginalTitle());
    }

    @Test
    @DisplayName("뉴스 발행이 완료되면 상태가 PUBLISHED로 변경된다")
    void publish_setsPublishedStatus() {
        News news = createNews();
        news.completeReconstruction("AI가 쓴 제목", "한 줄 요약", "재구성된 뉴스 본문");

        news.publish();

        assertEquals(NewsStatus.PUBLISHED, news.getStatus());
    }

    @Test
    @DisplayName("AI 처리 중인 뉴스는 바로 발행할 수 없다")
    void publish_throwsWhenNewsIsStillProcessing() {
        News news = createNews();

        assertThrows(IllegalStateException.class, news::publish);
    }

    @Test
    @DisplayName("뉴스 발행 처리에 실패하면 상태가 FAILED로 변경된다")
    void fail_setsFailedStatus() {
        News news = createNews();

        news.fail();

        assertEquals(NewsStatus.FAILED, news.getStatus());
    }

    @Test
    @DisplayName("뉴스 조회 시 조회수가 1 증가한다")
    void increaseViewCount_incrementsViewCount() {
        News news = createNews();

        news.increaseViewCount();

        assertEquals(1L, news.getViewCount());
    }

    @Test
    @DisplayName("뉴스를 삭제하면 삭제 시간이 기록된다")
    void delete_setsDeletedAt() {
        News news = createNews();

        news.delete();

        assertNotNull(news.getDeletedAt());
    }

    @Test
    @DisplayName("뉴스에 용어를 추가하면 내부 엔티티 목록에 포함된다")
    void addTerm_addsNewsTerm() {
        News news = createNews();

        assertTrue(news.addTerm(10L));
        assertTrue(news.addTerm(20L));

        assertEquals(2, news.getTerms().size());
        assertEquals(10L, news.getTerms().get(0).getTermId());
        assertEquals(20L, news.getTerms().get(1).getTermId());
    }

    @Test
    @DisplayName("이미 연결된 용어를 다시 추가하면 중복으로 저장하지 않는다")
    void addTerm_skipsDuplicatedTerm() {
        News news = createNews();

        assertTrue(news.addTerm(10L));
        assertFalse(news.addTerm(10L));

        assertEquals(1, news.getTerms().size());
        assertEquals(10L, news.getTerms().getFirst().getTermId());
    }

    @Test
    @DisplayName("용어 ID가 없으면 추가할 수 없다")
    void addTerm_throwsWhenTermIdIsNull() {
        News news = createNews();

        assertThrows(IllegalArgumentException.class, () -> news.addTerm(null));
    }

    @Test
    @DisplayName("뉴스 용어 목록은 외부에서 직접 수정할 수 없다")
    void getTerms_returnsUnmodifiableView() {
        News news = createNews();
        news.addTerm(10L);

        assertThrows(UnsupportedOperationException.class, () -> news.getTerms().add(null));
    }

    private News createNews() {
        return News.processing(
                CATEGORY_ID,
                "경제 뉴스",
                "muffin",
                PUBLISHED_AT,
                "https://example.com/thumb.png",
                "https://example.com/news/1");
    }
}
