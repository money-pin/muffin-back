package com.muffin.auth.domain.deletedaccount;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;

/** DeletedAccount 리포지토리. */
public interface DeletedAccountRepository extends JpaRepository<DeletedAccount, Long> {

    boolean existsByIdentityHashAndDeletedAtAfter(String identityHash, LocalDateTime cutoff);
}
