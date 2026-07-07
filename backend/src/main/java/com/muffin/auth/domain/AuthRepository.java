package com.muffin.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/** Auth 애그리거트 리포지토리 */
public interface AuthRepository extends JpaRepository<Auth, Long> {}
