package com.muffin.auth.infrastructure;

import com.muffin.auth.application.DeletedEmailProperties;
import com.muffin.auth.domain.deletedemail.EmailHasher;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 서버 비밀키(pepper)로 이메일을 HMAC-SHA256 해시한다. 무솔트 SHA-256과 달리 유출된 이메일 목록을 그대로 대입해
 * 원문을 역추적할 수 없다.
 */
@Component
public class HmacEmailHasher implements EmailHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public HmacEmailHasher(DeletedEmailProperties properties) {
        this.key = new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    @Override
    public String hash(String rawEmail) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(rawEmail.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
