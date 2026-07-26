package com.muffin.news.application.term;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTerm;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.response.TermSaveResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermCommandService {

    private final TermDictionaryRepository termDictionaryRepository;
    private final UserSavedTermRepository userSavedTermRepository;
    private final UserRepository userRepository;

    /** 사용자가 선택한 용어를 학습 저장소에 저장한다. 이미 저장된 용어는 기존 저장 상태를 그대로 반환한다. */
    @Transactional
    public TermSaveResponse saveTerm(Long userId, Long termId) {
        validateUser(userId);
        TermDictionary term = findTerm(termId);

        UserSavedTerm savedTerm = userSavedTermRepository
                .findByUserIdAndTermId(userId, termId)
                .orElseGet(() -> userSavedTermRepository.save(UserSavedTerm.create(userId, termId)));

        return new TermSaveResponse(term.getId(), term.getTerm(), true, savedTerm.getSavedAt());
    }

    /** 사용자가 저장한 용어를 학습 저장소에서 해제한다. 이미 해제된 용어도 성공 상태로 반환해 멱등성을 보장한다. */
    @Transactional
    public TermSaveResponse unsaveTerm(Long userId, Long termId) {
        validateUser(userId);
        TermDictionary term = findTerm(termId);

        userSavedTermRepository.findByUserIdAndTermId(userId, termId).ifPresent(userSavedTermRepository::delete);

        return new TermSaveResponse(term.getId(), term.getTerm(), false, null);
    }

    private void validateUser(Long userId) {
        if (userId == null) {
            throw new GeneralException(GeneralErrorCode.UNAUTHORIZED);
        }
        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));
        if (!user.isOnboardingCompleted()) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private TermDictionary findTerm(Long termId) {
        return termDictionaryRepository
                .findById(termId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_TERM_NOT_FOUND));
    }
}
