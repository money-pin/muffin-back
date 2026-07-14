package com.muffin.news.domain.category;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Category aggregate root repository. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);
}
