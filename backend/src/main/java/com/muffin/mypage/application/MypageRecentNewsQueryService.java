package com.muffin.mypage.application;

import com.muffin.mypage.application.projection.RecentNewsProjection;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.dto.RecentNewsResponse;
import com.muffin.mypage.presentation.dto.RecentNewsResponse.RecentNewsItem;
import com.muffin.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최근 읽은 뉴스 목록 조회 읽기 서비스.
 * <p>요청 값(size/cursor)을 검증하고, 사용자 존재를 확인한 뒤, 열람 시각(read_at) 내림차순으로 커서 페이지네이션 조회한다.
 */
@Service
@RequiredArgsConstructor
public class MypageRecentNewsQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final RecentNewsQueryRepository recentNewsQueryRepository;
    private final RecentNewsCursorCodec recentNewsCursorCodec;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public RecentNewsResponse getRecentNews(Long userId, String cursorParam, int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new MypageException(
                    MypageErrorCode.INVALID_PAGE_REQUEST, "size: 1 이상 50 이하여야 합니다. (입력: " + size + ")");
        }

        RecentNewsCursor cursor =
                (cursorParam == null || cursorParam.isBlank()) ? null : recentNewsCursorCodec.decode(cursorParam);

        if (!userRepository.existsById(userId)) {
            throw new MypageException(MypageErrorCode.USER_NOT_FOUND, null);
        }

        List<RecentNewsProjection> projections = recentNewsQueryRepository.findRecentNewsPage(userId, cursor, size + 1);
        boolean hasNext = projections.size() > size;
        List<RecentNewsProjection> pageProjections = hasNext ? projections.subList(0, size) : projections;

        List<RecentNewsItem> items = pageProjections.stream().map(this::toItem).toList();
        String nextCursor = hasNext ? encodeCursor(pageProjections.get(pageProjections.size() - 1)) : null;
        return new RecentNewsResponse(items, nextCursor, hasNext);
    }

    private RecentNewsItem toItem(RecentNewsProjection projection) {
        return new RecentNewsItem(
                projection.newsId(),
                projection.title(),
                projection.categoryName(),
                originalThumbnail(projection.thumbnailUrl()),
                projection.viewCount(),
                toKst(projection.publishedAt()),
                toKst(projection.viewedAt()));
    }

    /** 원본 썸네일이 있으면 그대로, 없으면(빈 문자열 포함) null을 반환한다. */
    private static String originalThumbnail(String thumbnailUrl) {
        return (thumbnailUrl != null && !thumbnailUrl.isBlank()) ? thumbnailUrl : null;
    }

    private String encodeCursor(RecentNewsProjection last) {
        return recentNewsCursorCodec.encode(last.viewedAt(), last.readHistoryId());
    }

    private OffsetDateTime toKst(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
