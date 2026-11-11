package com.muffin.briefing.application.generation;

import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.global.event.BriefingPublishedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 발행 시각에 도달한 브리핑을 사용자에게 공개한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingPublicationService {

    private final BriefingRepository briefingRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 기준일까지의 발행 대기 브리핑을 공개한다.
     *
     * <p>기준일 하루치만 보지 않고 그 이전까지 함께 집는 이유는, 스케줄러가 한 번 건너뛰면 그날 브리핑이 READY로 영영 남기 때문이다.
     *
     * @return 공개한 브리핑 수
     */
    @Transactional
    public int publish(LocalDate briefingDate, LocalDateTime publishedAt) {
        List<Briefing> targets =
                briefingRepository.findByStatusAndBriefingDateLessThanEqual(BriefingStatus.READY, briefingDate);

        for (Briefing briefing : targets) {
            briefing.publish(publishedAt);
            log.info("Briefing published: briefingDate={}", briefing.getBriefingDate());
            eventPublisher.publishEvent(new BriefingPublishedEvent(briefing.getBriefingDate(), publishedAt));
        }
        return targets.size();
    }

    /** 지정한 날짜의 브리핑이 이미 공개됐는지. 늦은 발행 재시도가 할 일이 없는 경우를 구분하는 데 쓴다. */
    @Transactional(readOnly = true)
    public boolean isPublished(LocalDate briefingDate) {
        return briefingRepository
                .findByBriefingDateAndStatus(briefingDate, BriefingStatus.PUBLISHED)
                .isPresent();
    }
}
