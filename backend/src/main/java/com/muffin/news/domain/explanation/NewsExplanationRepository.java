package com.muffin.news.domain.explanation;

import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** NewsExplanation 리포지토리 */
public interface NewsExplanationRepository extends JpaRepository<NewsExplanation, Long> {

    boolean existsByNewsIdAndStatus(Long newsId, NewsExplanationStatus status);

    List<NewsExplanation> findTop3ByNewsIdAndStatusOrderByCardOrderAsc(Long newsId, NewsExplanationStatus status);
}
