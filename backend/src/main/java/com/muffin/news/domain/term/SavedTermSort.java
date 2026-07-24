package com.muffin.news.domain.term;

import java.util.Locale;

/** 저장한 용어 목록의 정렬 기준. 최근 저장순(RECENT) 또는 가나다순(ALPHABETICAL). */
public enum SavedTermSort {
    RECENT,
    ALPHABETICAL;

    public static final SavedTermSort DEFAULT = RECENT;

    /**
     * 요청 문자열을 정렬 기준으로 해석한다. null/blank면 기본값({@link #DEFAULT})을 쓰고, 대소문자는 무시한다.
     *
     * @throws IllegalArgumentException 허용되지 않는 값인 경우
     */
    public static SavedTermSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return SavedTermSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("sort는 recent 또는 alphabetical만 허용됩니다. 입력값: " + raw);
        }
    }
}
