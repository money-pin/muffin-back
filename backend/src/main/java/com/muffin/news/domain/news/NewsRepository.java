package com.muffin.news.domain.news;

import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.enums.NewsStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** News aggregate root repository. NewsTerm is saved and loaded through News. */
public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByOriginalUrl(String originalUrl);

    List<News> findAllByStatus(NewsStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM News n WHERE n.id = :id")
    Optional<News> findByIdForUpdate(@Param("id") Long id);

    @Query(
            """
            SELECT n
            FROM News n
            WHERE n.status = :status
              AND n.publishedAt >= :startInclusive
              AND n.publishedAt < :endExclusive
              AND n.deletedAt IS NULL
              AND n.summary IS NOT NULL
              AND n.content IS NOT NULL
              AND EXISTS (
                  SELECT e.id
                  FROM NewsExplanation e
                  WHERE e.newsId = n.id
                    AND e.status = :explanationStatus
              )
            ORDER BY n.publishedAt DESC
            """)
    List<News> findQuizCandidates(
            @Param("status") NewsStatus status,
            @Param("explanationStatus") NewsExplanationStatus explanationStatus,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);

    @Query(
            """
            SELECT n
            FROM News n
            WHERE n.status = :status
              AND n.publishedAt >= :startInclusive
              AND n.publishedAt < :endExclusive
              AND n.deletedAt IS NULL
              AND n.summary IS NOT NULL
              AND n.content IS NOT NULL
            ORDER BY n.publishedAt DESC
            """)
    List<News> findReconstructedQuizCandidates(
            @Param("status") NewsStatus status,
            @Param("startInclusive") LocalDateTime startInclusive,
            @Param("endExclusive") LocalDateTime endExclusive,
            Pageable pageable);
}
