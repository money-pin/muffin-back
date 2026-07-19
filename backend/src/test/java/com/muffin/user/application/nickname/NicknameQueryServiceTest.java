package com.muffin.user.application.nickname;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.muffin.user.domain.UserRepository;
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
}
