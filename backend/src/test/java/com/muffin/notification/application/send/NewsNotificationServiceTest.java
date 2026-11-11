package com.muffin.notification.application.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NewsNotificationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final long ECONOMY = 1L;
    private static final long STOCK = 2L;

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final NotificationRecipientQueryRepository recipientQueryRepository =
            mock(NotificationRecipientQueryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final NewsNotificationService service = new NewsNotificationService(
            newsRepository,
            categoryRepository,
            recipientQueryRepository,
            dispatcher,
            Clock.fixed(TODAY.atTime(9, 0).atZone(KST).toInstant(), KST));

    @Test
    @DisplayName("대표 뉴스는 오늘의 뉴스와 같이 앞선 카테고리(경제)의 최신 뉴스이고, 본문에 나머지 건수를 붙인다")
    void picksHeadlineByTodayNewsOrder() {
        News stockLatest = news(10L, STOCK, "증권 최신", LocalDateTime.of(2026, 10, 20, 6, 0));
        News economyOld = news(11L, ECONOMY, "경제 이전", LocalDateTime.of(2026, 10, 19, 22, 0));
        News economyLatest = news(12L, ECONOMY, "경제 최신", LocalDateTime.of(2026, 10, 20, 5, 0));
        when(newsRepository.findAllById(List.of(10L, 11L, 12L)))
                .thenReturn(List.of(stockLatest, economyOld, economyLatest));
        List<Category> categories = List.of(category(ECONOMY, "경제"), category(STOCK, "증권"));
        when(categoryRepository.findAllById(anyList())).thenReturn(categories);
        when(recipientQueryRepository.findRecipientUserIds(NotificationType.NEWS))
                .thenReturn(List.of(1L));
        when(dispatcher.dispatchAll(any(), anyString(), anyList(), any(PushMessage.class)))
                .thenReturn(NotificationDispatchSummary.empty());

        service.notifyPublished(List.of(10L, 11L, 12L));

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(dispatcher).dispatchAll(eq(NotificationType.NEWS), eq("2026-10-20"), eq(List.of(1L)), message.capture());
        assertThat(message.getValue().body()).isEqualTo("경제 최신 외 2건");
        assertThat(message.getValue().deepLink()).isEqualTo("muffin://news/12");
    }

    @Test
    @DisplayName("공개 상태인 뉴스가 하나도 없으면 보내지 않는다")
    void skipsWhenNothingPublished() {
        News deleted = mock(News.class);
        when(deleted.getStatus()).thenReturn(NewsStatus.FAILED);
        when(newsRepository.findAllById(List.of(10L))).thenReturn(List.of(deleted));

        NotificationDispatchSummary summary = service.notifyPublished(List.of(10L));

        assertThat(summary.skipReason()).isEqualTo("no_published_news");
        verify(dispatcher, never()).dispatchAll(any(), anyString(), anyList(), any(PushMessage.class));
    }

    private static News news(Long id, Long categoryId, String title, LocalDateTime publishedAt) {
        News news = mock(News.class);
        when(news.getId()).thenReturn(id);
        when(news.getCategoryId()).thenReturn(categoryId);
        when(news.getTitle()).thenReturn(title);
        when(news.getPublishedAt()).thenReturn(publishedAt);
        when(news.getStatus()).thenReturn(NewsStatus.PUBLISHED);
        return news;
    }

    private static Category category(Long id, String name) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        return category;
    }
}
