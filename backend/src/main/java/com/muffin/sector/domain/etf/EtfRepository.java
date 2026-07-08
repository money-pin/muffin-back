package com.muffin.sector.domain.etf;

import org.springframework.data.jpa.repository.JpaRepository;

/** Etf 애그리거트 리포지토리. */
public interface EtfRepository extends JpaRepository<Etf, Long> {}
