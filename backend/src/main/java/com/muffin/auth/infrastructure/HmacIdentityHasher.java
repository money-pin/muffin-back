package com.muffin.auth.infrastructure;

import com.muffin.auth.application.DeletedAccountProperties;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 서버 비밀키(pepper)로 "PROVIDER:sub"를 HMAC-SHA256 해시한다. 무솔트 SHA-256과 달리 식별자를 알아도 비밀키 없이는
 * 해시를 재현할 수 없다. provider를 붙이는 이유는 provider가 다르면 같은 sub 문자열이 다른 사람이기 때문이다. sub는
 * 대소문자를 구분하는 값이라 소문자 정규화를 하지 않는다.
 */
@Component
public class HmacIdentityHasher implements IdentityHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacIdentityHasher(DeletedAccountProperties properties) {
        this.key = new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String hash(AuthProvider provider, String providerUserId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((provider.name() + ":" + providerUserId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
