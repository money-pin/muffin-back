package com.muffin.sector.domain.etf;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Etf 애그리거트 리포지토리. */
public interface EtfRepository extends JpaRepository<Etf, Long> {

    Optional<Etf> findByEtfCode(String etfCode);
}
