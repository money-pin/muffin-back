package com.muffin.character.domain.characterprofile;

import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캐릭터 애그리거트 루트. */
@Getter
@Entity
// character는 MySQL 예약어라 백틱 없이는 DDL이 실패한다. 엔티티명에 맞춰 character_profile을 쓴다.
@Table(name = "character_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_id")
    private Long characterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "muffin_type", nullable = false, unique = true, length = 20)
    private MuffinType muffinType;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    private CharacterProfile(MuffinType muffinType, String name, String description, String imageUrl) {
        this.muffinType = muffinType;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public static CharacterProfile create(MuffinType muffinType, String name, String description, String imageUrl) {
        return new CharacterProfile(muffinType, name, description, imageUrl);
    }
}
