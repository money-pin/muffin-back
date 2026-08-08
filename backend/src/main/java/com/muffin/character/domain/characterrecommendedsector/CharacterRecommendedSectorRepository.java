package com.muffin.character.domain.characterrecommendedsector;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** CharacterRecommendedSector 리포지토리 */
public interface CharacterRecommendedSectorRepository extends JpaRepository<CharacterRecommendedSector, Long> {

    List<CharacterRecommendedSector> findByCharacterIdOrderById(Long characterId);
}
