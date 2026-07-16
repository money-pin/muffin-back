package com.muffin.character.domain;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "character")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "character_id")
    private Long characterId;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    private CharacterProfile(String name, String description, String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public static CharacterProfile create(String name, String description, String imageUrl) {
        return new CharacterProfile(name, description, imageUrl);
    }
}
