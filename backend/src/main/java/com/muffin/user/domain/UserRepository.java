package com.muffin.user.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** User 애그리거트 루트 리포지토리. 내부 엔티티(UserOnboarding)는 루트를 통해 저장/조회되므로 별도 리포지토리를 두지 않는다. */
public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByNickname(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);
}
