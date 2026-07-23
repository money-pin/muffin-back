package com.muffin.news.application.term;

import com.muffin.news.domain.term.SavedTermSort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

/** 저장한 용어 목록 조회 읽기 전용 인터페이스. */
public interface SavedTermQueryRepository {

    /** 사용자가 저장한 용어를 정렬 기준에 따라 페이지 단위로 조회한다. count 쿼리 없이 hasNext만 판정하는 {@link Slice}를 반환한다. */
    Slice<SavedTermRow> findSavedTerms(Long userId, SavedTermSort sort, Pageable pageable);
}
