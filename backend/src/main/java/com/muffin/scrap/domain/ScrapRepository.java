package com.muffin.scrap.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Scrap 애그리거트 리포지토리 */
public interface ScrapRepository extends JpaRepository<Scrap, Long> {

    boolean existsByUserIdAndNewsId(Long userId, Long newsId);

    Optional<Scrap> findByUserIdAndNewsId(Long userId, Long newsId);

    void deleteByUserIdAndNewsId(Long userId, Long newsId);
}
