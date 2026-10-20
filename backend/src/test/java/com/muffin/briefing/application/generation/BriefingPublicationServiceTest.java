package com.muffin.briefing.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.global.event.BriefingPublishedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class BriefingPublicationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final LocalDateTime PUBLISHED_AT = TODAY.atTime(7, 30);

    private final BriefingRepository briefingRepository = mock(BriefingRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final BriefingPublicationService service =
            new BriefingPublicationService(briefingRepository, eventPublisher);

    @Test
    @DisplayName("공개한 브리핑마다 기준일과 공개 시각을 담아 이벤트를 발행한다. 알림은 이 값으로 오늘 브리핑인지 판단한다")
    void publishesEventPerBriefing() {
        Briefing missed = readyBriefing(TODAY.minusDays(1));
        Briefing today = readyBriefing(TODAY);
        when(briefingRepository.findByStatusAndBriefingDateLessThanEqual(BriefingStatus.READY, TODAY))
                .thenReturn(List.of(missed, today));

        int published = service.publish(TODAY, PUBLISHED_AT);

        assertThat(published).isEqualTo(2);
        verify(eventPublisher).publishEvent(new BriefingPublishedEvent(TODAY.minusDays(1), PUBLISHED_AT));
        verify(eventPublisher).publishEvent(new BriefingPublishedEvent(TODAY, PUBLISHED_AT));
    }

    private static Briefing readyBriefing(LocalDate date) {
        Briefing briefing = mock(Briefing.class);
        when(briefing.getBriefingDate()).thenReturn(date);
        return briefing;
    }
}
