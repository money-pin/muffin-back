package com.muffin.user.application.onboarding;

import com.muffin.character.domain.CharacterProfile;
import com.muffin.character.domain.CharacterRecommendedSector;
import com.muffin.character.domain.CharacterRecommendedSectorRepository;
import com.muffin.character.domain.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.exception.UserErrorCode;
import com.muffin.user.presentation.onboarding.dto.CharacterResultRequest;
import com.muffin.user.presentation.onboarding.dto.CharacterResultResponse;
import com.muffin.user.presentation.onboarding.dto.RecommendedSectorResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 온보딩 설문 결과에 따라 캐릭터를 확정 저장하는 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional
public class CharacterResultCommandService {

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final CharacterRecommendedSectorRepository characterRecommendedSectorRepository;
    private final SectorRepository sectorRepository;

    public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));

        MuffinType muffinType = MuffinType.from(request.muffin());
        CharacterProfile character = characterRepository
                .findByMuffinType(muffinType)
                .orElseThrow(() -> new GeneralException(UserErrorCode.CHARACTER_NOT_FOUND));

        user.assignCharacter(character.getCharacterId());
        user.completeOnboarding(request.firstQuestion(), request.secondQuestion(), request.thirdQuestion());

        return new CharacterResultResponse(
                character.getCharacterId(),
                character.getMuffinType().name(),
                character.getName(),
                character.getDescription(),
                character.getImageUrl(),
                resolveRecommendedSectors(character.getCharacterId()));
    }

    private List<RecommendedSectorResponse> resolveRecommendedSectors(Long characterId) {
        List<Long> sectorIds = characterRecommendedSectorRepository.findByCharacterIdOrderById(characterId).stream()
                .map(CharacterRecommendedSector::getSectorId)
                .toList();

        Map<Long, Sector> sectorsById = sectorRepository.findAllById(sectorIds).stream()
                .collect(Collectors.toMap(Sector::getId, Function.identity()));

        return sectorIds.stream()
                .map(sectorsById::get)
                .filter(Objects::nonNull)
                .map(sector -> new RecommendedSectorResponse(sector.getSectorCode(), sector.getName()))
                .toList();
    }
}
