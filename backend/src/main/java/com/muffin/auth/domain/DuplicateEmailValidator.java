package com.muffin.auth.domain;

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
