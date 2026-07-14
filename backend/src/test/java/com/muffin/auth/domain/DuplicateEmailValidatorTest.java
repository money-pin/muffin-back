package com.muffin.auth.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuplicateEmailValidatorTest {

    @Mock
    private AuthRepository authRepository;

    @InjectMocks
    private DuplicateEmailValidator duplicateEmailValidator;

    @Test
    @DisplayName("이미 사용 중인 이메일 → DuplicateEmailException")
    void duplicateEmail() {
        when(authRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> duplicateEmailValidator.validate("test@example.com"))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    @DisplayName("사용 중이 아닌 이메일 → 예외 없음")
    void notDuplicate() {
        when(authRepository.existsByEmail("test@example.com")).thenReturn(false);

        assertThatNoException().isThrownBy(() -> duplicateEmailValidator.validate("test@example.com"));
    }
}
