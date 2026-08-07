package com.muffin.auth.domain.deletedemail;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /** emailHash는 {@link com.muffin.auth.domain.deletedemail.EmailHasher}로 미리 해시한 값을 전달해야 한다. */
    public static DeletedEmail of(String emailHash) {
        return new DeletedEmail(emailHash);
    }
}
