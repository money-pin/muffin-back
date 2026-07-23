package com.muffin.mypage.application;

import com.muffin.mypage.domain.ScrapSort;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.dto.ScrapListResponse.ScrapItem;
import com.muffin.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스크랩한 뉴스 목록 조회 읽기 서비스.
 *
 * <p>요청 값(size/sort/cursor)을 검증하고, 사용자 존재를 확인한 뒤, 정렬 기준으로 커서 페이지네이션 조회한다. 시각은 전역 {@code Clock}의
 * zone(KST)으로 오프셋을 붙여 내려준다.
 */
@Service
@RequiredArgsConstructor
public class MypageScrapQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final ScrapListQueryRepository scrapListQueryRepository;
    private final ScrapCursorCodec scrapCursorCodec;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScrapListResponse getScraps(Long userId, String sortParam, String cursorParam, int size) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new MypageException(
                    MypageErrorCode.INVALID_PAGE_REQUEST, "size: 1 이상 50 이하여야 합니다. (입력: " + size + ")");
        }

        ScrapSort sort = ScrapSort.from(sortParam);
        ScrapCursor cursor =
                (cursorParam == null || cursorParam.isBlank()) ? null : scrapCursorCodec.decode(cursorParam);
        if (cursor != null && cursor.sort() != sort) {
            throw new MypageException(MypageErrorCode.INVALID_PAGE_REQUEST, "커서의 정렬 기준이 요청과 다릅니다.");
        }

        if (!userRepository.existsById(userId)) {
            throw new MypageException(MypageErrorCode.USER_NOT_FOUND, null);
        }

        List<ScrapListRow> rows = scrapListQueryRepository.findScrapPage(userId, sort, cursor, size + 1);
        boolean hasNext = rows.size() > size;
        List<ScrapListRow> pageRows = hasNext ? rows.subList(0, size) : rows;

        List<ScrapItem> items = pageRows.stream().map(this::toItem).toList();
        String nextCursor = hasNext ? encodeCursor(sort, pageRows.get(pageRows.size() - 1)) : null;
        return new ScrapListResponse(items, nextCursor, hasNext);
    }

    private ScrapItem toItem(ScrapListRow row) {
        return new ScrapItem(
                row.newsId(),
                row.title(),
                row.categoryName(),
                row.thumbnailUrl(),
                row.viewCount(),
                toKst(row.publishedAt()),
                toKst(row.scrappedAt()));
    }

    private String encodeCursor(ScrapSort sort, ScrapListRow last) {
        return switch (sort) {
            case SAVED_DESC -> scrapCursorCodec.encode(sort, last.scrappedAt(), null, last.scrapId());
            case PUBLISHED_DESC -> scrapCursorCodec.encode(sort, last.publishedAt(), null, last.newsId());
            case VIEW_DESC -> scrapCursorCodec.encode(sort, null, last.viewCount(), last.newsId());
        };
    }

    private OffsetDateTime toKst(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(clock.getZone()).toOffsetDateTime();
    }
}
