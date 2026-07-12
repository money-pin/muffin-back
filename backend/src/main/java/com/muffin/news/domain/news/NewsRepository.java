package com.muffin.news.domain.news;

import org.springframework.data.jpa.repository.JpaRepository;

/** News aggregate root repository. NewsTerm is saved and loaded through News. */
public interface NewsRepository extends JpaRepository<News, Long> {

    boolean existsByOriginalUrl(String originalUrl);
}
