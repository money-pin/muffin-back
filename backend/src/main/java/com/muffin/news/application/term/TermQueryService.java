package com.muffin.news.application.term;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.response.TermResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermQueryService {

    private final TermDictionaryRepository termDictionaryRepository;
    private final UserSavedTermRepository userSavedTermRepository;

    /** 용어 바텀시트에 표시할 사전 설명과 현재 사용자의 저장 여부를 조회한다. */
    @Transactional(readOnly = true)
    public TermResponse getTerm(Long userId, Long termId) {
        TermDictionary term = termDictionaryRepository
                .findById(termId)
                .orElseThrow(() -> new NewsException(NewsErrorCode.NEWS_TERM_NOT_FOUND));

        // 저장/해제 기능은 별도 이슈지만, 조회 응답의 isSaved를 위해 현재 저장 여부만 계산한다.
        boolean saved = userSavedTermRepository.existsByUserIdAndTermId(userId, termId);

        return new TermResponse(term.getId(), term.getTerm(), term.getContent(), saved);
    }
}
