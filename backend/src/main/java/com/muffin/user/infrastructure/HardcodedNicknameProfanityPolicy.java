package com.muffin.user.infrastructure;

import com.muffin.user.domain.NicknameProfanityPolicy;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link NicknameProfanityPolicy}의 임시 구현체. 실 서비스용 정식 비속어 사전이 아직 없어 하드코딩된 최소 목록으로 판정한다.
 * 운영 전 실제 비속어 사전(또는 외부 필터링 서비스)으로 교체해야 한다.
 */
@Component
public class HardcodedNicknameProfanityPolicy implements NicknameProfanityPolicy {

    private static final Set<String> BANNED_WORDS = Set.of("시발", "씨발", "병신", "개새끼", "fuck", "shit");

    @Override
    public boolean isProfane(String normalizedNickname) {
        String lower = normalizedNickname.toLowerCase();
        return BANNED_WORDS.stream().anyMatch(lower::contains);
    }
}
