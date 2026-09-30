package com.muffin.news.domain.sectorimpact;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** NewsSectorImpact 리포지토리 */
public interface NewsSectorImpactRepository extends JpaRepository<NewsSectorImpact, Long> {

    List<NewsSectorImpact> findByNewsId(Long newsId);

    /** 여러 뉴스의 섹터 영향도를 한 번에 읽는다. 브리핑 이슈 3건의 섹터 칩을 만들 때 쓴다. */
    List<NewsSectorImpact> findByNewsIdIn(Collection<Long> newsIds);
}
