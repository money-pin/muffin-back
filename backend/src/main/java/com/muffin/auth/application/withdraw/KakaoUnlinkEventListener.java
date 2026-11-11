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
public class KakaoUnlinkEventListener {

    private final KakaoAccountUnlinker kakaoAccountUnlinker;

    /** 외부 호출은 롤백할 수 없어 탈퇴 커밋 후에만 실행한다. 실패해도 탈퇴는 이미 끝났으므로 로그만 남긴다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(AccountWithdrawnEvent event) {
        if (event.provider() != AuthProvider.KAKAO) {
            return;
        }
        try {
            kakaoAccountUnlinker.unlink(event.providerUserId());
        } catch (RuntimeException exception) {
            log.error("Kakao unlink failed after withdrawal: kakaoUserId={}", event.providerUserId(), exception);
        }
    }
}
