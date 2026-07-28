package com.muffin.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.mypage.application.projection.ScrapListProjection;
import com.muffin.mypage.domain.ScrapSort;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 스크랩 목록 조회의 요청 검증/사용자 검증/커서 페이지네이션/시각 변환을 단위로 검증한다. */
class MypageScrapQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 5, 7, 9, 0, 0);
    private static final LocalDateTime SCRAPPED_AT = LocalDateTime.of(2026, 5, 8, 14, 30, 0);

    private ScrapListQueryRepository scrapListQueryRepository;
    private ScrapCursorCodec scrapCursorCodec;
    private UserRepository userRepository;
    private MypageScrapQueryService service;

    @BeforeEach
    void setUp() {
        scrapListQueryRepository = mock(ScrapListQueryRepository.class);
        scrapCursorCodec = mock(ScrapCursorCodec.class);
        userRepository = mock(UserRepository.class);
        Clock clock = Clock.fixed(SCRAPPED_AT.atZone(KST).toInstant(), KST);
        service = new MypageScrapQueryService(scrapListQueryRepository, scrapCursorCodec, userRepository, clock);
    }

    @Test
    @DisplayName("size가 1~50 범위를 벗어나면 MYPAGE_400_004 예외를 던진다")
    void getScraps_throwsWhenSizeOutOfRange() {
        assertThatThrownBy(() -> service.getScraps(USER_ID, null, null, 51))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
        verify(scrapListQueryRepository, never()).findScrapPage(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("허용되지 않는 sort 값이면 MYPAGE_400_004 예외를 던진다")
    void getScraps_throwsWhenSortInvalid() {
        assertThatThrownBy(() -> service.getScraps(USER_ID, "LATEST", null, 20))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
    }

    @Test
    @DisplayName("커서의 정렬 기준이 요청 정렬과 다르면 MYPAGE_400_004 예외를 던진다")
    void getScraps_throwsWhenCursorSortMismatches() {
        when(scrapCursorCodec.decode("CURSOR"))
                .thenReturn(new ScrapCursor(ScrapSort.PUBLISHED_DESC, PUBLISHED_AT, null, 10L));

        assertThatThrownBy(() -> service.getScraps(USER_ID, "SAVED_DESC", "CURSOR", 20))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
        verify(scrapListQueryRepository, never()).findScrapPage(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 MYPAGE_404_001 예외를 던진다")
    void getScraps_throwsWhenUserNotFound() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getScraps(USER_ID, null, null, 20))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("size+1개가 조회되면 hasNext=true로 자르고 마지막 행으로 nextCursor를 인코딩한다")
    void getScraps_paginatesAndEncodesNextCursor() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 3))
                .thenReturn(List.of(projection(10L, 100L), projection(11L, 101L), projection(12L, 102L)));
        when(scrapCursorCodec.encode(eq(ScrapSort.SAVED_DESC), any(), any(), anyLong()))
                .thenReturn("NEXT");

        ScrapListResponse response = service.getScraps(USER_ID, null, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("NEXT");
        assertThat(response.items().getFirst().newsId()).isEqualTo(10L);
        assertThat(response.items().getFirst().scrappedAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-08T14:30:00+09:00"));
        assertThat(response.items().getFirst().publishedAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-07T09:00:00+09:00"));
        // 저장순 커서는 마지막 페이지 행의 (scrappedAt, scrapId)로 만든다.
        verify(scrapCursorCodec).encode(ScrapSort.SAVED_DESC, SCRAPPED_AT, null, 101L);
    }

    @Test
    @DisplayName("조회 결과가 size 이하이면 hasNext=false이고 nextCursor는 없다")
    void getScraps_lastPageHasNoNextCursor() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 3))
                .thenReturn(List.of(projection(10L, 100L)));

        ScrapListResponse response = service.getScraps(USER_ID, null, null, 2);

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("스크랩이 없으면 빈 목록과 hasNext=false를 반환한다")
    void getScraps_returnsEmptyWhenNoScraps() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 3))
                .thenReturn(List.of());

        ScrapListResponse response = service.getScraps(USER_ID, null, null, 2);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("썸네일이 빈 문자열이면 null로 정규화한다")
    void getScraps_normalizesBlankThumbnailToNull() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(scrapListQueryRepository.findScrapPage(USER_ID, ScrapSort.SAVED_DESC, null, 3))
                .thenReturn(List.of(
                        new ScrapListProjection(10L, "제목10", "반도체", "", 100L, PUBLISHED_AT, SCRAPPED_AT, 100L)));

        ScrapListResponse response = service.getScraps(USER_ID, null, null, 2);

        assertThat(response.items().getFirst().thumbnailUrl()).isNull();
    }

    private ScrapListProjection projection(Long newsId, Long scrapId) {
        return new ScrapListProjection(
                newsId, "제목" + newsId, "반도체", "https://thumb/" + newsId, 100L, PUBLISHED_AT, SCRAPPED_AT, scrapId);
    }
}
