package com.muffin.user.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** User 애그리거트 루트 리포지토리. 내부 엔티티(UserOnboarding)는 루트를 통해 저장/조회되므로 별도 리포지토리를 두지 않는다. */
public interface UserRepository extends JpaRepository<User, Long> {}
