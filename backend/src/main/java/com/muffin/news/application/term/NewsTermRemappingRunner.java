package com.muffin.news.application.term;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.news.application.term.NewsTermRemappingService.NewsTermRemappingReport;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기동 시 과거 뉴스의 용어 연결을 현재 매칭 규칙으로 다시 맞춘다.
 *
 * <p>기본값은 꺼짐이다. 매칭 규칙을 고친 배포에서만 {@code TERM_REMAP_ON_STARTUP=true}로 한 번 켜고, 로그에서
 * {@code batch job=term_remapping}을 확인한 뒤 다시 끈다. 상시로 켜 두지 않는 이유는 전체 뉴스 × 용어 사전을 매 기동마다
 * 훑게 되고, 그 비용이 기동 시간에 그대로 붙기 때문이다.
 *
 * <p>스케줄러로 만들지 않은 것도 같은 이유다. 이 작업은 규칙이 바뀔 때만 필요한 일회성 보정이라, 주기 실행으로 두면 평소에는 아무것도
 * 바꾸지 않으면서 비용만 쓴다.
 */
@Component
@Profile("!test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.term.remap-on-startup", havingValue = "true")
public class NewsTermRemappingRunner implements ApplicationRunner {

    private final NewsTermRemappingService remappingService;
    private final BatchJobRunner batchJobRunner;

    @Override
    public void run(ApplicationArguments args) {
        batchJobRunner.run(BatchJob.TERM_REMAPPING, BatchTrigger.STARTUP, () -> {
            NewsTermRemappingReport report = remappingService.remapAll();
            if (report.scannedNews() == 0) {
                return BatchJobReport.skipped("no_news");
            }
            return BatchJobReport.success()
                    .with("scanned", report.scannedNews())
                    .with("changed_news", report.changedNews())
                    .with("changed_links", report.changedLinks());
        });
    }
}
