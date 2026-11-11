package com.muffin.auth.domain.auth;

import com.muffin.auth.domain.enums.AuthProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Auth 애그리거트 리포지토리 */
public interface AuthRepository extends JpaRepository<Auth, Long> {

    Optional<Auth> findByUserId(Long userId);

    Optional<Auth> findByProviderAndProviderUserId(AuthProvider provider, String providerUserId);
}
