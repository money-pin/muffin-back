package com.muffin.briefing.domain;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingViewRepository extends JpaRepository<BriefingView, Long> {

    Optional<BriefingView> findByUserIdAndBriefingDate(Long userId, LocalDate briefingDate);
}
