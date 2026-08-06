package com.muffin.auth.domain.deletedemail;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 탈퇴한 계정의 이메일을 해시로만 보관한다(원문 이메일은 남기지 않음). 재가입 30일 제한에 사용한다. */
@Entity
@Table(name = "deleted_emails")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DeletedEmail extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deleted_email_id")
    private Long id;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    private DeletedEmail(String emailHash) {
        this.emailHash = emailHash;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public static DeletedEmail of(String rawEmail) {
        return new DeletedEmail(hash(rawEmail));
    }

    public static String hash(String rawEmail) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest(rawEmail.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
