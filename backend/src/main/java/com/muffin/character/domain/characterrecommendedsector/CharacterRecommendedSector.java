package com.muffin.character.domain.characterrecommendedsector;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 캐릭터별 추천 섹터. CharacterProfile, Sector 두 애그리거트를 ID로만 참조한다. */
@Getter
@Entity
@Table(
        name = "character_recommended_sector",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_character_recommended_sector_character_sector",
                        columnNames = {"character_id", "sector_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CharacterRecommendedSector extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommended_sector_id")
    private Long id;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    private CharacterRecommendedSector(Long characterId, Long sectorId) {
        this.characterId = characterId;
        this.sectorId = sectorId;
    }

    public static CharacterRecommendedSector create(Long characterId, Long sectorId) {
        return new CharacterRecommendedSector(characterId, sectorId);
    }
}
