package com.muffin.mypage.domain;

import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import java.util.Locale;

/**
 * 스크랩 목록 정렬 기준.
 *
 * <ul>
 *   <li>{@link #SAVED_DESC}: 최근 저장순(scrap.createdAt)
 *   <li>{@link #PUBLISHED_DESC}: 업로드순(news.publishedAt)
 *   <li>{@link #VIEW_DESC}: 조회수순(news.viewCount)
 * </ul>
 *
 * <p>세 정렬 모두 동률을 안정적으로 끊기 위해 각자의 타이브레이크 키(저장순=scrap.id, 그 외=news.id)를 함께 쓴다.
 */
public enum ScrapSort {
    SAVED_DESC,
    PUBLISHED_DESC,
    VIEW_DESC;

    /** 값을 지정하지 않으면 최근 저장순으로 본다. */
    public static final ScrapSort DEFAULT = SAVED_DESC;

    private static final String ALLOWED = "허용값: SAVED_DESC, PUBLISHED_DESC, VIEW_DESC";

    /**
     * 요청 문자열을 정렬 기준으로 해석한다. null/blank면 기본값({@link #DEFAULT})을 쓰고, 대소문자는 무시한다.
     *
     * @throws MypageException 허용되지 않는 값인 경우({@link MypageErrorCode#INVALID_PAGE_REQUEST})
     */
    public static ScrapSort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return ScrapSort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new MypageException(MypageErrorCode.INVALID_PAGE_REQUEST, "sort " + ALLOWED + " (입력: " + raw + ")");
        }
    }
}
