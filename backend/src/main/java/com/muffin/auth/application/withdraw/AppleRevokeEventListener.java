package com.muffin.auth.application.withdraw;

import com.muffin.auth.domain.enums.AuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppleRevokeEventListener {

    private final AppleAuthorizationClient appleAuthorizationClient;

    /** 외부 호출은 롤백할 수 없어 탈퇴 커밋 후에만 실행한다. 실패해도 탈퇴는 이미 끝났으므로 로그만 남긴다(토큰 값은 남기지 않음). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(AccountWithdrawnEvent event) {
        if (event.provider() != AuthProvider.APPLE || event.appleRefreshToken() == null) {
            return;
        }
        try {
            appleAuthorizationClient.revoke(event.appleRefreshToken());
        } catch (RuntimeException exception) {
            log.error("Apple token revoke failed after withdrawal: appleUserId={}", event.providerUserId(), exception);
        }
    }
}
