package com.muffin.news.domain.news;

import com.muffin.news.domain.news.enums.NewsStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** News aggregate root repository. NewsTerm is saved and loaded through News. */
public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByOriginalUrl(String originalUrl);

    List<News> findAllByStatus(NewsStatus status);

    List<News> findAllByStatusAndPublishedAtBetweenAndDeletedAtIsNullOrderByPublishedAtDesc(
            NewsStatus status, LocalDateTime startInclusive, LocalDateTime endExclusive);
}
