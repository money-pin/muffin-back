package com.muffin.auth.domain.deletedemail;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

/** DeletedEmail 리포지토리. */
public interface DeletedEmailRepository extends JpaRepository<DeletedEmail, Long> {

    boolean existsByEmailHashAndDeletedAtAfter(String emailHash, LocalDateTime cutoff);
}
