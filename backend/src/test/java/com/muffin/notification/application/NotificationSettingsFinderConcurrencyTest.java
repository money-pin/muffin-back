package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * {@link NotificationSettingsFinder}의 동시 생성 레이스 방어를 실제 DB에 대한 두 개의 독립된 트랜잭션으로 재현한다.
 *
 * <p>같은 트랜잭션 안에서 순차 저장을 흉내 내는 방식(다른 유니크 제약 테스트들의 방식)은 이 엔티티에는 통하지 않는다: {@code
 * NotificationSettings}는 {@code userId}를 수동 할당 PK로 쓰기 때문에 {@code JpaRepository#save}가 {@code
 * isNew()==false}로 판단해 insert가 아닌 merge 경로를 타고, 같은 세션 안에서는 먼저 flush된 행을 그냥 찾아 갱신해버려 제약
 * 위반이 재현되지 않는다. 실제 두 트랜잭션이 각자 빈 상태를 보고 동시에 insert를 시도해야만 DB가 PK 충돌을 낸다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, NotificationSettingsFinder.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationSettingsFinderConcurrencyTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private NotificationSettingsFinder notificationSettingsFinder;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @Test
    @DisplayName("두 요청이 동시에 findOrCreate를 호출해도 알림 설정 행은 하나만 생성되고 둘 다 그 행을 반환한다")
    void concurrentFindOrCreate_createsExactlyOneRow() throws Exception {
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<NotificationSettings>> tasks = IntStream.range(0, requestCount)
                .<Callable<NotificationSettings>>mapToObj(i -> () -> {
                    ready.countDown();
                    start.await();
                    return notificationSettingsFinder.findOrCreate(USER_ID);
                })
                .toList();

        List<Future<NotificationSettings>> futures = new ArrayList<>();
        for (Callable<NotificationSettings> task : tasks) {
            futures.add(executor.submit(task));
        }

        ready.await();
        start.countDown();

        List<NotificationSettings> results = new ArrayList<>();
        for (Future<NotificationSettings> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(results).extracting(NotificationSettings::getUserId).containsOnly(USER_ID);
        assertThat(notificationSettingsRepository.findAll()).hasSize(1);
    }
}
