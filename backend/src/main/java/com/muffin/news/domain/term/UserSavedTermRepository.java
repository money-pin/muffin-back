package com.muffin.news.domain.term;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** UserSavedTerm repository. */
public interface UserSavedTermRepository extends JpaRepository<UserSavedTerm, Long> {

    boolean existsByUserIdAndTermId(Long userId, Long termId);

    Optional<UserSavedTerm> findByUserIdAndTermId(Long userId, Long termId);
}
