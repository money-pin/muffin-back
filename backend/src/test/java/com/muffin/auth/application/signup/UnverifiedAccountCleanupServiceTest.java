package com.muffin.auth.application.signup;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.refreshtoken.RefreshToken;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * TTL이 지난 미인증(LOCAL, emailVerified=false) 계정만 User/Auth/RefreshToken까지 함께 삭제하고, 나머지는
 * 남기는지 검증한다. created_at은 JPA Auditing이 관리해 도메인 API로 조작할 수 없으므로 네이티브 쿼리로 직접
 * 되돌리는데(backdateCreatedAt), 이 쿼리가 활성 트랜잭션을 요구해 클래스 전체를 @Transactional로 감싼다(각 테스트
 * 종료 시 자동 롤백되어 별도 @AfterEach 정리도 불필요).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnverifiedAccountCleanupServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Autowired
    private UnverifiedAccountCleanupService unverifiedAccountCleanupService;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    private User createUser() {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
    }

    private Auth createLocalAuth(Long userId, String email, boolean emailVerified) {
        Auth auth = Auth.createLocal(userId, email, "password1", "encoded");
        if (emailVerified) {
            auth.verifyEmail();
        }
        return authRepository.save(auth);
    }

    private void backdateCreatedAt(Auth auth, LocalDateTime createdAt) {
        entityManager
                .createNativeQuery("UPDATE auth SET created_at = :createdAt WHERE auth_id = :authId")
                .setParameter("createdAt", createdAt)
                .setParameter("authId", auth.getAuthId())
                .executeUpdate();
        entityManager.clear();
    }

    @Test
    @DisplayName("TTL이 지난 미인증 계정은 User/Auth/RefreshToken이 모두 삭제된다")
    void cleanupUnverified_deletesExpiredUnverifiedAccountAndRelatedRows() {
        User user = createUser();
        Auth auth = createLocalAuth(user.getUserId(), "expired-unverified@example.com", false);
        backdateCreatedAt(auth, LocalDateTime.now(KST).minusMinutes(1441));
        refreshTokenRepository.save(RefreshToken.issue(
                user.getUserId(), "hash", LocalDateTime.now(KST).plusDays(30)));

        unverifiedAccountCleanupService.cleanupUnverified();

        assertThat(authRepository.findById(auth.getAuthId())).isEmpty();
        assertThat(userRepository.findById(user.getUserId())).isEmpty();
        assertThat(refreshTokenRepository.findByUserId(user.getUserId())).isEmpty();
    }

    @Test
    @DisplayName("TTL이 아직 지나지 않은 미인증 계정은 남는다")
    void cleanupUnverified_keepsNotYetExpiredUnverifiedAccount() {
        User user = createUser();
        Auth auth = createLocalAuth(user.getUserId(), "recent-unverified@example.com", false);
        backdateCreatedAt(auth, LocalDateTime.now(KST).minusMinutes(1));

        unverifiedAccountCleanupService.cleanupUnverified();

        assertThat(authRepository.findById(auth.getAuthId())).isPresent();
        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    @Test
    @DisplayName("TTL이 지났어도 이메일 인증을 완료한 계정은 남는다")
    void cleanupUnverified_keepsVerifiedAccountEvenIfOld() {
        User user = createUser();
        Auth auth = createLocalAuth(user.getUserId(), "old-verified@example.com", true);
        backdateCreatedAt(auth, LocalDateTime.now(KST).minusMinutes(1441));

        unverifiedAccountCleanupService.cleanupUnverified();

        assertThat(authRepository.findById(auth.getAuthId())).isPresent();
        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }

    @Test
    @DisplayName("TTL이 지났어도 GOOGLE 계정은 대상이 아니다")
    void cleanupUnverified_keepsGoogleAccount() {
        User user = createUser();
        Auth auth = authRepository.save(Auth.createGoogle(user.getUserId(), "old-google@example.com", "google-sub-1"));
        backdateCreatedAt(auth, LocalDateTime.now(KST).minusMinutes(1441));

        unverifiedAccountCleanupService.cleanupUnverified();

        assertThat(authRepository.findById(auth.getAuthId())).isPresent();
        assertThat(userRepository.findById(user.getUserId())).isPresent();
    }
}
