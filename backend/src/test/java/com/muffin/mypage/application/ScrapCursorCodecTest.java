package com.muffin.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.mypage.domain.ScrapSort;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 스크랩 커서 인코딩/디코딩 라운드트립과 변조 커서 거부를 검증한다. */
class ScrapCursorCodecTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 5, 8, 14, 30, 0);
    private final ScrapCursorCodec codec = new ScrapCursorCodec(new ObjectMapper());

    @Test
    @DisplayName("시각 기준 정렬(SAVED/PUBLISHED)은 timeKey를 담아 라운드트립된다")
    void encodeDecode_timeKeySorts() {
        String encoded = codec.encode(ScrapSort.SAVED_DESC, TIME, null, 101L);

        ScrapCursor decoded = codec.decode(encoded);

        assertThat(decoded.sort()).isEqualTo(ScrapSort.SAVED_DESC);
        assertThat(decoded.timeKey()).isEqualTo(TIME);
        assertThat(decoded.numberKey()).isNull();
        assertThat(decoded.id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("조회수 정렬(VIEW_DESC)은 numberKey를 담아 라운드트립된다")
    void encodeDecode_numberKeySort() {
        String encoded = codec.encode(ScrapSort.VIEW_DESC, null, 3120L, 55L);

        ScrapCursor decoded = codec.decode(encoded);

        assertThat(decoded.sort()).isEqualTo(ScrapSort.VIEW_DESC);
        assertThat(decoded.timeKey()).isNull();
        assertThat(decoded.numberKey()).isEqualTo(3120L);
        assertThat(decoded.id()).isEqualTo(55L);
    }

    @Test
    @DisplayName("형식이 깨진 커서는 MYPAGE_400_004로 거부한다")
    void decode_rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("!!not-base64-json!!"))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
    }
}
