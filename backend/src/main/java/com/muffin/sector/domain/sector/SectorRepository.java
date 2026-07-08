package com.muffin.sector.domain.sector;

import org.springframework.data.jpa.repository.JpaRepository;

/** Sector 애그리거트 리포지토리. */
public interface SectorRepository extends JpaRepository<Sector, Long> {}
