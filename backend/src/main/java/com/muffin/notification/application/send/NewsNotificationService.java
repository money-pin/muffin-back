package com.muffin.notification.application.send;

import com.muffin.news.application.query.NewsQueryService;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NOTI-05 뉴스 업데이트 알림. 뉴스를 공개할 때마다가 아니라 하루 한 번만 보낸다.
 *
 * <p>대표 뉴스는 '오늘의 뉴스' 첫 항목과 같은 기준으로 고른다. 카테고리 순서(경제 → 증권 → 세계)에서 앞선 카테고리의 최신 뉴스다. 알림을 탭해 들어간
 * 화면과 오늘의 뉴스 첫 카드가 달라지지 않게 하기 위해서다.
 */
@Service
@RequiredArgsConstructor
public class NewsNotificationService {

    private final NewsRepository newsRepository;
    private final CategoryRepository categoryRepository;
    private final NotificationRecipientQueryRepository recipientQueryRepository;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public NotificationDispatchSummary notifyPublished(List<Long> publishedNewsIds) {
        List<News> published = newsRepository.findAllById(publishedNewsIds).stream()
                .filter(news -> news.getStatus() == NewsStatus.PUBLISHED && news.getDeletedAt() == null)
                .toList();
        Optional<News> headline = selectHeadline(published);
        if (headline.isEmpty()) {
            return NotificationDispatchSummary.skipped("no_published_news");
        }

        LocalDate today = LocalDate.now(clock);
        return dispatcher.dispatchAll(
                NotificationType.NEWS,
                today.toString(),
                recipientQueryRepository.findRecipientUserIds(NotificationType.NEWS),
                NotificationMessages.news(headline.get().getId(), headline.get().getTitle(), published.size()));
    }

    private Optional<News> selectHeadline(List<News> published) {
        Map<Long, String> categoryNames =
                categoryRepository
                        .findAllById(published.stream()
                                .map(News::getCategoryId)
                                .distinct()
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));
        List<String> categoryOrder = NewsQueryService.TODAY_NEWS_CATEGORIES;

        Function<News, Integer> categoryRank = news -> {
            int index = categoryOrder.indexOf(categoryNames.get(news.getCategoryId()));
            return index < 0 ? categoryOrder.size() : index;
        };
        return published.stream()
                .min(Comparator.comparing(categoryRank)
                        .thenComparing(
                                News::getPublishedAt, Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
                        .thenComparing(News::getId, Comparator.reverseOrder()));
    }
}
