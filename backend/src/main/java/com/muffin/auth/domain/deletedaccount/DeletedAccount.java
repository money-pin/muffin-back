package com.muffin.auth.domain.deletedaccount;

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

/**
 * 탈퇴한 소셜 계정(provider+sub)을 해시로만 보관한다(원본 식별자는 남기지 않음). 재가입 30일 제한에 사용한다. 탈퇴 시 Auth의
 * sub는 익명화되므로, 탈퇴 이후 이 계정과 다시 들어온 소셜 계정을 연결할 수 있는 건 이 해시뿐이다.
 */
@Entity
@Table(name = "deleted_accounts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class DeletedAccount extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deleted_account_id")
    private Long id;

    @Column(name = "identity_hash", nullable = false, length = 64)
    private String identityHash;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    private DeletedAccount(String identityHash) {
        this.identityHash = identityHash;
        this.deletedAt = LocalDateTime.now(KST);
    }

    /** identityHash는 {@link IdentityHasher}로 미리 해시한 값을 전달해야 한다. */
    public static DeletedAccount of(String identityHash) {
        return new DeletedAccount(identityHash);
    }
}
