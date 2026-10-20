package com.muffin.auth.application.social;

import com.muffin.auth.domain.SocialIdTokenVerifier;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.enums.AuthProvider;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** provider에 맞는 ID Token 검증기를 골라 검증한다. 로그인과 탈퇴(애플 계정 확인)가 함께 쓴다. */
@Component
@RequiredArgsConstructor
public class SocialIdTokenVerifiers {

    private final List<SocialIdTokenVerifier> verifiers;

    /**
     * 목록에서 매번 찾는다. 기동 시점에 Map으로 만들어 두면, 테스트가 verifier를 목으로 바꿨을 때 아직 스텁되지 않은
     * provider()가 null을 돌려줘 컨텍스트 기동이 깨진다. verifier는 provider 수(최대 3개)만큼이라 비용은 무시할 수준이다.
     */
    public SocialIdentity verify(AuthProvider provider, String idToken) {
        return verifiers.stream()
                .filter(verifier -> verifier.provider() == provider)
                .findFirst()
                // IllegalStateException은 전역 처리기가 409와 함께 메시지를 응답에 넣으므로 쓰지 않는다(500, 응답에 상세 없음).
                .orElseThrow(() -> new UnsupportedOperationException("ID Token 검증기가 등록되지 않은 provider입니다: " + provider))
                .verify(idToken);
    }
}
