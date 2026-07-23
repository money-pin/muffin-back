package com.muffin.user.application.nickname;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.NicknameProfanityPolicy;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.exception.UserErrorCode;
import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NicknameQueryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NicknameProfanityPolicy profanityPolicy;

    @InjectMocks
    private NicknameQueryService nicknameQueryService;

    @Test
    @DisplayName("이미 존재하는 닉네임 → available=false")
    void alreadyTaken() {
        when(userRepository.existsByNickname("길동이")).thenReturn(true);

        assertFalse(nicknameQueryService.isAvailable("길동이"));
    }

    @Test
    @DisplayName("존재하지 않는 닉네임 → available=true")
    void available() {
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);

        assertTrue(nicknameQueryService.isAvailable("새닉네임"));
    }

    @Test
    @DisplayName("분해형(NFD) 입력 → 완성형(NFC)으로 정규화해 조회")
    void normalizesDecomposedHangul() {
        String decomposed = Normalizer.normalize("닉네임", Normalizer.Form.NFD);
        when(userRepository.existsByNickname("닉네임")).thenReturn(false);

        assertTrue(nicknameQueryService.isAvailable(decomposed));
    }

    @Test
    @DisplayName("2자 미만 → IllegalArgumentException")
    void tooShort() {
        assertThatThrownBy(() -> nicknameQueryService.isAvailable("일")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("10자 초과 → IllegalArgumentException")
    void tooLong() {
        assertThatThrownBy(() -> nicknameQueryService.isAvailable("일이삼사오육칠팔구십일"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("비속어 포함 → NICKNAME_CONTAINS_PROFANITY")
    void containsProfanity() {
        when(profanityPolicy.isProfane("나쁜말포함")).thenReturn(true);

        assertThatThrownBy(() -> nicknameQueryService.isAvailable("나쁜말포함"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> {
                    GeneralException generalException = (GeneralException) ex;
                    org.assertj.core.api.Assertions.assertThat(generalException.getErrorCode())
                            .isEqualTo(UserErrorCode.NICKNAME_CONTAINS_PROFANITY);
                });
    }
}
