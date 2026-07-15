package com.muffin.sector.domain.sectorgroup;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** SectorGroup 애그리거트 리포지토리. */
public interface SectorGroupRepository extends JpaRepository<SectorGroup, Long> {

    Optional<SectorGroup> findByGroupCode(String groupCode);
}
