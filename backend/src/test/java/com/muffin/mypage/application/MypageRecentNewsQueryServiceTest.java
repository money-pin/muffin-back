package com.muffin.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.mypage.application.projection.RecentNewsProjection;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.dto.RecentNewsResponse;
import com.muffin.user.domain.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 최근 읽은 뉴스 목록 조회의 요청 검증/사용자 검증/커서 페이지네이션/시각 변환을 단위로 검증한다. */
class MypageRecentNewsQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 5, 7, 9, 0, 0);
    private static final LocalDateTime VIEWED_AT = LocalDateTime.of(2026, 5, 8, 20, 15, 0);

    private RecentNewsQueryRepository recentNewsQueryRepository;
    private RecentNewsCursorCodec recentNewsCursorCodec;
    private UserRepository userRepository;
    private MypageRecentNewsQueryService service;

    @BeforeEach
    void setUp() {
        recentNewsQueryRepository = mock(RecentNewsQueryRepository.class);
        recentNewsCursorCodec = mock(RecentNewsCursorCodec.class);
        userRepository = mock(UserRepository.class);
        Clock clock = Clock.fixed(VIEWED_AT.atZone(KST).toInstant(), KST);
        service = new MypageRecentNewsQueryService(
                recentNewsQueryRepository, recentNewsCursorCodec, userRepository, clock);
    }

    @Test
    @DisplayName("size가 1~50 범위를 벗어나면 MYPAGE_400_004 예외를 던진다")
    void getRecentNews_throwsWhenSizeOutOfRange() {
        assertThatThrownBy(() -> service.getRecentNews(USER_ID, null, 51))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
        verify(recentNewsQueryRepository, never()).findRecentNewsPage(any(), any(), anyInt());
    }

    @Test
    @DisplayName("size가 1 미만이면 MYPAGE_400_004로 거부하고 사용자 조회/커서 디코딩으로 넘어가지 않는다")
    void getRecentNews_throwsWhenSizeBelowMin() {
        assertThatThrownBy(() -> service.getRecentNews(USER_ID, "CURSOR", 0))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
        verify(userRepository, never()).existsById(anyLong());
        verify(recentNewsCursorCodec, never()).decode(any());
        verify(recentNewsQueryRepository, never()).findRecentNewsPage(any(), any(), anyInt());
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 MYPAGE_404_001 예외를 던진다")
    void getRecentNews_throwsWhenUserNotFound() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getRecentNews(USER_ID, null, 20))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("size+1개가 조회되면 hasNext=true로 자르고 마지막 행으로 nextCursor를 인코딩한다")
    void getRecentNews_paginatesAndEncodesNextCursor() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 3))
                .thenReturn(List.of(projection(10L, 100L), projection(11L, 101L), projection(12L, 102L)));
        when(recentNewsCursorCodec.encode(any(), anyLong())).thenReturn("NEXT");

        RecentNewsResponse response = service.getRecentNews(USER_ID, null, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("NEXT");
        assertThat(response.items().getFirst().newsId()).isEqualTo(10L);
        assertThat(response.items().getFirst().viewedAt()).isEqualTo(OffsetDateTime.parse("2026-05-08T20:15:00+09:00"));
        assertThat(response.items().getFirst().publishedAt())
                .isEqualTo(OffsetDateTime.parse("2026-05-07T09:00:00+09:00"));
        // 커서는 마지막 페이지 행의 (viewedAt, readHistoryId)로 만든다.
        verify(recentNewsCursorCodec).encode(VIEWED_AT, 101L);
    }

    @Test
    @DisplayName("조회 결과가 size 이하이면 hasNext=false이고 nextCursor는 없다")
    void getRecentNews_lastPageHasNoNextCursor() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 3)).thenReturn(List.of(projection(10L, 100L)));

        RecentNewsResponse response = service.getRecentNews(USER_ID, null, 2);

        assertThat(response.items()).hasSize(1);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    @DisplayName("커서가 있으면 디코딩해 그대로 저장소 조회에 전달한다")
    void getRecentNews_decodesCursorAndPassesToRepository() {
        RecentNewsCursor cursor = new RecentNewsCursor(VIEWED_AT, 100L);
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(recentNewsCursorCodec.decode("CURSOR")).thenReturn(cursor);
        when(recentNewsQueryRepository.findRecentNewsPage(USER_ID, cursor, 3))
                .thenReturn(List.of(projection(10L, 100L)));

        RecentNewsResponse response = service.getRecentNews(USER_ID, "CURSOR", 2);

        assertThat(response.items()).hasSize(1);
        verify(recentNewsCursorCodec).decode("CURSOR");
        verify(recentNewsQueryRepository).findRecentNewsPage(USER_ID, cursor, 3);
    }

    @Test
    @DisplayName("공백 커서는 디코딩하지 않고 첫 페이지(cursor=null)로 조회한다")
    void getRecentNews_blankCursorSkipsDecode() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 3)).thenReturn(List.of());

        service.getRecentNews(USER_ID, "   ", 2);

        verify(recentNewsCursorCodec, never()).decode(any());
        verify(recentNewsQueryRepository).findRecentNewsPage(USER_ID, null, 3);
    }

    @Test
    @DisplayName("읽은 뉴스가 없으면 빈 목록과 hasNext=false를 반환한다")
    void getRecentNews_returnsEmptyWhenNone() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(recentNewsQueryRepository.findRecentNewsPage(USER_ID, null, 3)).thenReturn(List.of());

        RecentNewsResponse response = service.getRecentNews(USER_ID, null, 2);

        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
        assertThat(response.nextCursor()).isNull();
    }

    private RecentNewsProjection projection(Long newsId, Long readHistoryId) {
        return new RecentNewsProjection(
                newsId, "제목" + newsId, "반도체", "https://thumb/" + newsId, 100L, PUBLISHED_AT, VIEWED_AT, readHistoryId);
    }
}
