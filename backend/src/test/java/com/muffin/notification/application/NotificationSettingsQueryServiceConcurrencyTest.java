package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.notification.domain.NotificationSettingsRepository;
import com.muffin.notification.presentation.dto.MypageSettingsResponse;
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
 * {@code NotificationSettingsQueryService.getSettings}(호출자 트랜잭션)를 두 스레드가 동시에 호출해도
 * 정상 완료되는지 실제 DB로 검증한다.
 *
 * <p>같은 트랜잭션 안에서 순차 저장을 흉내 내는 방식(다른 유니크 제약 테스트들의 방식)은 이 엔티티에는 통하지 않는다: {@code
 * NotificationSettings}는 {@code userId}를 수동 할당 PK로 쓰기 때문에 {@code JpaRepository#save}가 {@code
 * isNew()==false}로 판단해 insert가 아닌 merge 경로를 타고, 같은 세션 안에서는 먼저 flush된 행을 그냥 찾아 갱신해버려 제약
 * 위반이 재현되지 않는다. 실제 두 트랜잭션이 각자 빈 상태를 보고 동시에 insert를 시도해야만 DB가 PK 충돌을 낸다.
 *
 * <p>{@code getSettings}를 직접 호출 지점으로 삼는 이유: {@link NotificationSettingsFinder#findOrCreate}를
 * 바로 호출하면 그 호출을 감싸는 호출자 트랜잭션이 없어서, 저장 실패가 호출자 트랜잭션을 rollback-only로 오염시키는 문제
 * (호출자 트랜잭션 없이 저장을 시도하면 재현되지 않음)를 검증하지 못한다. {@code getSettings}는 실제 서비스와 동일하게
 * {@code @Transactional} 경계를 갖고 있어, 이 경계 안에서 저장이 실패해도 호출자 트랜잭션이 오염되지 않고 정상적으로
 * 커밋되는지(즉 {@code UnexpectedRollbackException}이 나지 않는지)를 검증할 수 있다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({
    JpaAuditingConfig.class,
    NotificationSettingsQueryService.class,
    NotificationSettingsFinder.class,
    NotificationSettingsCreator.class
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationSettingsQueryServiceConcurrencyTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private NotificationSettingsQueryService notificationSettingsQueryService;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @Test
    @DisplayName("두 요청이 동시에 getSettings를 호출해도 둘 다 예외 없이 완료되고 알림 설정 행은 하나만 생성된다")
    void concurrentGetSettings_bothSucceedAndCreateExactlyOneRow() throws Exception {
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<MypageSettingsResponse>> tasks = IntStream.range(0, requestCount)
                .<Callable<MypageSettingsResponse>>mapToObj(i -> () -> {
                    ready.countDown();
                    start.await();
                    return notificationSettingsQueryService.getSettings(USER_ID);
                })
                .toList();

        List<Future<MypageSettingsResponse>> futures = new ArrayList<>();
        for (Callable<MypageSettingsResponse> task : tasks) {
            futures.add(executor.submit(task));
        }

        ready.await();
        start.countDown();

        List<MypageSettingsResponse> results = new ArrayList<>();
        for (Future<MypageSettingsResponse> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        assertThat(results).hasSize(requestCount).doesNotContainNull();
        assertThat(notificationSettingsRepository.findAll()).hasSize(1);
    }
}
