package com.muffin.news.domain.explanation;

import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** NewsExplanation 리포지토리 */
public interface NewsExplanationRepository extends JpaRepository<NewsExplanation, Long> {

    boolean existsByNewsIdAndStatus(Long newsId, NewsExplanationStatus status);

    List<NewsExplanation> findTop3ByNewsIdAndStatusOrderByCardOrderAsc(Long newsId, NewsExplanationStatus status);

    /** 여러 뉴스의 해설카드를 한 번에 읽는다. 브리핑 후보 수십 건에 건별 조회를 돌리지 않기 위한 것이다. */
    List<NewsExplanation> findByNewsIdInAndStatus(Collection<Long> newsIds, NewsExplanationStatus status);
}
