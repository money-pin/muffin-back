package com.muffin.user.application.nickname;

import com.muffin.user.domain.NicknameProfanityPolicy;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 중복 조회 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NicknameQueryService {

    private final UserRepository userRepository;
    private final NicknameProfanityPolicy profanityPolicy;

    public boolean isAvailable(String nickname) {
        String normalized = User.normalizeAndValidateNickname(nickname);
        if (profanityPolicy.isProfane(normalized)) {
            throw new UserException(UserErrorCode.NICKNAME_CONTAINS_PROFANITY);
        }
        return !userRepository.existsByNickname(normalized);
    }
}
