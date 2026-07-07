package com.muffin.news.domain.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.news.domain.news.enums.NewsStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsTest {

    private static final Long CATEGORY_ID = 1L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 7, 9, 0);

    @Test
    @DisplayName("뉴스를 생성하면 발행 대기 상태와 조회수 0으로 초기화된다")
    void create_initializesPendingStatusAndZeroViewCount() {
        News news = createNews();

        assertEquals(NewsStatus.PENDING, news.getStatus());
        assertEquals(0L, news.getViewCount());
    }

    @Test
    @DisplayName("뉴스 발행이 완료되면 상태가 PUBLISHED로 변경된다")
    void publish_setsPublishedStatus() {
        News news = createNews();

        news.publish();

        assertEquals(NewsStatus.PUBLISHED, news.getStatus());
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

        news.addTerm(10L);
        news.addTerm(20L);

        assertEquals(2, news.getTerms().size());
        assertEquals(10L, news.getTerms().get(0).getTermId());
        assertEquals(20L, news.getTerms().get(1).getTermId());
    }

    @Test
    @DisplayName("뉴스 용어 목록은 외부에서 직접 수정할 수 없다")
    void getTerms_returnsUnmodifiableView() {
        News news = createNews();
        news.addTerm(10L);

        assertThrows(UnsupportedOperationException.class, () -> news.getTerms().add(null));
    }

    private News createNews() {
        return News.create(
                CATEGORY_ID,
                "경제 뉴스",
                "muffin",
                PUBLISHED_AT,
                "https://example.com/thumb.png",
                "https://example.com/news/1",
                "뉴스 본문");
    }
}
