package com.muffin.news.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

/** Category aggregate root repository. */
public interface CategoryRepository extends JpaRepository<Category, Long> {}
