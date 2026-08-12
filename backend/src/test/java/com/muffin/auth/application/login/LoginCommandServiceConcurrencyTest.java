package com.muffin.auth.application.login;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 동일 계정에 대한 동시 로그인 실패 요청이 lost update 없이 정확히 집계되는지 실제 DB로 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class LoginCommandServiceConcurrencyTest {

    private static final String EMAIL = "concurrency-login@example.com";
    private static final String PASSWORD = "password1";
    private static final int MAX_ATTEMPTS = 5;

    @Autowired
    private LoginCommandService loginCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private void registerLocalAccount() {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createLocal(user.getUserId(), EMAIL, PASSWORD, passwordEncoder.encode(PASSWORD)));
    }

    @Test
    @DisplayName("동일 계정에 잘못된 비밀번호 요청 5건을 동시에 보내도 실패 횟수가 정확히 5로 집계되어 계정이 잠긴다")
    void concurrentFailedLogins_countExactlyAndLock() throws Exception {
        registerLocalAccount();

        ExecutorService executor = Executors.newFixedThreadPool(MAX_ATTEMPTS);
        CountDownLatch ready = new CountDownLatch(MAX_ATTEMPTS);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<Void>> tasks = IntStream.range(0, MAX_ATTEMPTS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    ready.countDown();
                    start.await();
                    try {
                        loginCommandService.loginLocal(EMAIL, "wrongpass1");
                    } catch (GeneralException ignored) {
                        // 실패는 기대된 결과. 여기서는 최종 집계만 검증한다.
                    }
                    return null;
                })
                .toList();

        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(executor.submit(task));
            }

            ready.await();
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        Auth reloaded =
                authRepository.findByProviderAndEmail(AuthProvider.LOCAL, EMAIL).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(reloaded.isLoginLocked()).isTrue();
    }
}
