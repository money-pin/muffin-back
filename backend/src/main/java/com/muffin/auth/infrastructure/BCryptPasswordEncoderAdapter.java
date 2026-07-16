package com.muffin.auth.infrastructure;

import com.muffin.auth.domain.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BCryptPasswordEncoderAdapter implements PasswordEncoder {

    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder();

    @Override
    public String encode(String raw) {
        return delegate.encode(raw);
    }

    @Override
    public boolean matches(String raw, String encoded) {
        return delegate.matches(raw, encoded);
    }
}
