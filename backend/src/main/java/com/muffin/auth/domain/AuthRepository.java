package com.muffin.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Auth 애그리거트 리포지토리 */
public interface AuthRepository extends JpaRepository<Auth, Long> {

    boolean existsByEmail(String email);

    Optional<Auth> findByUserId(Long userId);
}
