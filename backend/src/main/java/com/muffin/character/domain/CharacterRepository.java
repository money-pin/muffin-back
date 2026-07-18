package com.muffin.character.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** CharacterProfile 애그리거트 리포지토리 */
public interface CharacterRepository extends JpaRepository<CharacterProfile, Long> {

    Optional<CharacterProfile> findByName(String name);
}
