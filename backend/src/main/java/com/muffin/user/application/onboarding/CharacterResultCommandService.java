package com.muffin.user.application.onboarding;

import com.muffin.character.domain.characterprofile.CharacterProfile;
import com.muffin.character.domain.characterprofile.CharacterRepository;
import com.muffin.character.domain.characterrecommendedsector.CharacterRecommendedSector;
import com.muffin.character.domain.characterrecommendedsector.CharacterRecommendedSectorRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
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
    private final OnboardingCompletionService onboardingCompletionService;

    public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        CharacterProfile character = characterRepository
                .findByMuffinType(request.muffin())
                .orElseThrow(() -> new UserException(UserErrorCode.CHARACTER_NOT_FOUND));

        user.assignCharacter(character.getCharacterId());
        user.completeOnboarding(request.firstQuestion(), request.secondQuestion(), request.thirdQuestion());
        // 온보딩 완료 직후 바로 홈으로 진입하므로 이 시점에 초기 자산을 지급한다. 여기서 실패해도
        // /api/onboarding/complete(초기 자산 API)가 없는 경우에만 보정 지급한다(멱등).
        onboardingCompletionService.ensureInitialAssetGranted(userId);

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
