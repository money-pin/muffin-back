package com.muffin.user.application.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import com.muffin.user.presentation.onboarding.dto.CharacterResultRequest;
import com.muffin.user.presentation.onboarding.dto.CharacterResultResponse;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CharacterResultCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long CHARACTER_ID = 10L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterRecommendedSectorRepository characterRecommendedSectorRepository;

    @Mock
    private SectorRepository sectorRepository;

    @InjectMocks
    private CharacterResultCommandService characterResultCommandService;

    private User user;
    private CharacterProfile character;

    @BeforeEach
    void setUp() {
        user = User.register(null, UUID.randomUUID().toString(), "홍길동", null);
        character =
                CharacterProfile.create(MuffinType.PLAIN, "플레인 머핀", "기본 플레인 머핀 캐릭터", "https://example.com/plain.png");
        ReflectionTestUtils.setField(character, "characterId", CHARACTER_ID);

        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        lenient().when(characterRepository.findByMuffinType(MuffinType.PLAIN)).thenReturn(Optional.of(character));
    }

    private Sector sector(Long id, String sectorCode, String name) {
        Sector sector = Sector.create(1L, 1L, name, null, sectorCode, 1);
        ReflectionTestUtils.setField(sector, "id", id);
        return sector;
    }

    @Test
    @DisplayName("정상 요청 → 캐릭터 확정, 온보딩 완료, 추천 섹터 순서 보존해 반환")
    void success() {
        Sector semiconductor = sector(100L, "SEMICONDUCTOR", "반도체");
        Sector gold = sector(200L, "GOLD", "금");
        when(characterRecommendedSectorRepository.findByCharacterIdOrderById(CHARACTER_ID))
                .thenReturn(List.of(
                        CharacterRecommendedSector.create(CHARACTER_ID, 100L),
                        CharacterRecommendedSector.create(CHARACTER_ID, 200L)));
        when(sectorRepository.findAllById(any())).thenReturn(List.of(gold, semiconductor));

        CharacterResultResponse response =
                characterResultCommandService.submit(USER_ID, new CharacterResultRequest(MuffinType.PLAIN, 1, 2, 3));

        assertThat(response.characterId()).isEqualTo(CHARACTER_ID);
        assertThat(response.characterType()).isEqualTo("PLAIN");
        assertThat(response.characterName()).isEqualTo("플레인 머핀");
        assertThat(response.recommendedSectors()).hasSize(2);
        assertThat(response.recommendedSectors().get(0).sectorCode()).isEqualTo("SEMICONDUCTOR");
        assertThat(response.recommendedSectors().get(1).sectorCode()).isEqualTo("GOLD");
        assertThat(user.getCharacterId()).isEqualTo(CHARACTER_ID);
        assertThat(user.isOnboardingCompleted()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 사용자 → GeneralException(USER_NOT_FOUND)")
    void userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterResultCommandService.submit(
                        USER_ID, new CharacterResultRequest(MuffinType.PLAIN, 1, 2, 3)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("존재하지 않는 캐릭터 → GeneralException(CHARACTER_NOT_FOUND)")
    void characterNotFound() {
        when(characterRepository.findByMuffinType(MuffinType.SPRINKLE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> characterResultCommandService.submit(
                        USER_ID, new CharacterResultRequest(MuffinType.SPRINKLE, 1, 2, 3)))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("이미 온보딩을 완료한 사용자 재제출 → IllegalStateException")
    void alreadyCompleted() {
        user.completeOnboarding(1, 1, 1);

        assertThatThrownBy(() -> characterResultCommandService.submit(
                        USER_ID, new CharacterResultRequest(MuffinType.PLAIN, 1, 2, 3)))
                .isInstanceOf(IllegalStateException.class);
    }
}
