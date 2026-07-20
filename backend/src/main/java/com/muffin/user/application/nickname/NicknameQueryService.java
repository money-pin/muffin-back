package com.muffin.user.application.nickname;

import com.muffin.user.domain.UserRepository;
import java.text.Normalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 중복 조회 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NicknameQueryService {

    private final UserRepository userRepository;

    public boolean isAvailable(String nickname) {
        String normalized = Normalizer.normalize(nickname, Normalizer.Form.NFC);
        return !userRepository.existsByNickname(normalized);
    }
}
