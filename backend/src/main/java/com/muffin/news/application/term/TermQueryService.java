package com.muffin.news.application.term;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.SavedTermSort;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.SavedTermListResponse;
import com.muffin.news.presentation.dto.SavedTermListResponse.SavedTermItem;
import com.muffin.news.presentation.dto.TermResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermQueryService {

    private static final int MAX_SAVED_TERM_PAGE_SIZE = 50;

    private final TermDictionaryRepository termDictionaryRepository;
    private final UserSavedTermRepository userSavedTermRepository;
    private final SavedTermQueryRepository savedTermQueryRepository;

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

    /** 마이페이지에서 사용자가 저장한 용어 목록을 페이지 단위로 조회한다. 최근 저장순 또는 가나다순으로 정렬할 수 있다. */
    @Transactional(readOnly = true)
    public SavedTermListResponse getSavedTerms(Long userId, Pageable pageable, String sortParam) {
        if (pageable.getPageSize() > MAX_SAVED_TERM_PAGE_SIZE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "size: 50 이하여야 합니다.");
        }
        SavedTermSort sort = SavedTermSort.from(sortParam);
        Slice<SavedTermRow> slice = savedTermQueryRepository.findSavedTerms(userId, sort, pageable);

        var items = slice.getContent().stream()
                .map(row -> new SavedTermItem(row.termId(), row.term(), row.content(), row.savedAt()))
                .toList();

        return new SavedTermListResponse(items, slice.getNumber(), slice.getSize(), slice.hasNext());
    }
}
