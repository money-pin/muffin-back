package com.muffin.auth.domain.emailverification;

import com.muffin.auth.domain.AuthRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DuplicateEmailValidator {

    private final AuthRepository authRepository;

    public void validate(String email) {
        if (authRepository.existsByEmail(email)) {
            throw new DuplicateEmailException(email);
        }
    }
}
