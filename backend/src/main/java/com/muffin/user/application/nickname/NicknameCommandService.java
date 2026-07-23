package com.muffin.user.application.nickname;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.NicknameProfanityPolicy;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 닉네임 변경 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional
public class NicknameCommandService {

    private static final String NICKNAME_UNIQUE_CONSTRAINT = "uk_member_nickname";

    private final UserRepository userRepository;
    private final NicknameProfanityPolicy profanityPolicy;

    public String changeNickname(Long userId, String newNickname) {
        String normalized = User.normalizeAndValidateNickname(newNickname);
        if (profanityPolicy.isProfane(normalized)) {
            throw new GeneralException(UserErrorCode.NICKNAME_CONTAINS_PROFANITY);
        }
        if (userRepository.existsByNickname(normalized)) {
            throw new GeneralException(UserErrorCode.NICKNAME_DUPLICATED);
        }

        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(UserErrorCode.USER_NOT_FOUND));
        user.changeNickname(normalized);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // existsByNickname 이후 커밋 전 동시 변경 레이스: DB unique 제약이 최종 방어선.
            if (!ConstraintViolations.isConstraint(e, NICKNAME_UNIQUE_CONSTRAINT)) {
                throw e;
            }
            throw new GeneralException(UserErrorCode.NICKNAME_DUPLICATED);
        }
        return user.getNickname();
    }
}
