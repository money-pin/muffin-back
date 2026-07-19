package com.muffin.news.domain.readhistory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** ReadHistory 애그리거트 리포지토리 */
public interface ReadHistoryRepository extends JpaRepository<ReadHistory, Long> {

    Optional<ReadHistory> findByUserIdAndNewsId(Long userId, Long newsId);
}
