package com.muffin.briefing.domain;

import com.muffin.briefing.domain.enums.BriefingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingRepository extends JpaRepository<Briefing, Long> {

    Optional<Briefing> findByBriefingDate(LocalDate briefingDate);

    Optional<Briefing> findByBriefingDateAndStatus(LocalDate briefingDate, BriefingStatus status);

    /** 발행 시각이 되어 공개할 대상. 하루 한 건이지만 누락 복구를 위해 과거 READY도 함께 집는다. */
    List<Briefing> findByStatusAndBriefingDateLessThanEqual(BriefingStatus status, LocalDate briefingDate);

    /** 최근 발행분. 오늘 브리핑이 없을 때의 폴백과 날짜 선택 칩 목록에 함께 쓴다. */
    List<Briefing> findByStatusOrderByBriefingDateDesc(BriefingStatus status, Pageable pageable);
}
