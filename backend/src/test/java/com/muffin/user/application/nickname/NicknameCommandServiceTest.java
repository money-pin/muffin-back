package com.muffin.user.application.nickname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.NicknameProfanityPolicy;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.exception.UserErrorCode;
import java.text.Normalizer;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NicknameCommandServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NicknameProfanityPolicy profanityPolicy;

    @InjectMocks
    private NicknameCommandService nicknameCommandService;

    private User newUser() {
        return User.register(null, UUID.randomUUID().toString(), "홍길동", null);
    }

    @Test
    @DisplayName("정상 변경 → 닉네임이 갱신된다")
    void success() {
        User user = newUser();
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        String result = nicknameCommandService.changeNickname(USER_ID, "새닉네임");

        assertThat(result).isEqualTo("새닉네임");
        assertThat(user.getNickname()).isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임 → NICKNAME_DUPLICATED")
    void duplicated() {
        when(userRepository.existsByNickname("중복닉네임")).thenReturn(true);

        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "중복닉네임"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("존재하지 않는 유저 → USER_NOT_FOUND")
    void userNotFound() {
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "새닉네임"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex ->
                        assertThat(((GeneralException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("비속어 포함 → NICKNAME_CONTAINS_PROFANITY (중복 체크보다 먼저 검증)")
    void containsProfanity() {
        when(profanityPolicy.isProfane("나쁜말포함")).thenReturn(true);

        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "나쁜말포함"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_CONTAINS_PROFANITY));
    }

    @Test
    @DisplayName("2자 미만 → IllegalArgumentException")
    void tooShort() {
        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "일"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("10자 초과 → IllegalArgumentException")
    void tooLong() {
        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "일이삼사오육칠팔구십일"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("동시 변경 레이스: existsByNickname 이후 저장 시점에 unique 제약 위반 → NICKNAME_DUPLICATED")
    void raceOnSave_uniqueConstraintViolation_translatedToDuplicated() {
        User user = newUser();
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "could not execute statement; constraint [uk_member_nickname]"));

        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "새닉네임"))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.NICKNAME_DUPLICATED));
    }

    @Test
    @DisplayName("저장 시점의 다른 종류의 무결성 위반은 닉네임 중복으로 잘못 번역하지 않고 그대로 던진다")
    void saveFailure_unrelatedConstraint_rethrownAsIs() {
        User user = newUser();
        when(userRepository.existsByNickname("새닉네임")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        DataIntegrityViolationException unrelated =
                new DataIntegrityViolationException("constraint [uk_member_user_uuid]");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> nicknameCommandService.changeNickname(USER_ID, "새닉네임"))
                .isSameAs(unrelated);
    }

    @Test
    @DisplayName("분해형(NFD) 입력 → 완성형(NFC)으로 정규화해 저장")
    void normalizesDecomposedHangul() {
        User user = newUser();
        String decomposed = Normalizer.normalize("닉네임", Normalizer.Form.NFD);
        when(userRepository.existsByNickname("닉네임")).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        String result = nicknameCommandService.changeNickname(USER_ID, decomposed);

        assertThat(result).isEqualTo("닉네임");
    }
}
