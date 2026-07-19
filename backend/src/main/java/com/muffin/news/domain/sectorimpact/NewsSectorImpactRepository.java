package com.muffin.news.domain.sectorimpact;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** NewsSectorImpact 리포지토리 */
public interface NewsSectorImpactRepository extends JpaRepository<NewsSectorImpact, Long> {

    List<NewsSectorImpact> findByNewsId(Long newsId);
}
