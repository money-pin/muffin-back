package com.muffin.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 최근 읽은 뉴스 커서 인코딩/디코딩 라운드트립과 변조 커서 거부를 검증한다. */
class RecentNewsCursorCodecTest {

    private static final LocalDateTime VIEWED_AT = LocalDateTime.of(2026, 5, 8, 20, 15, 0);
    private final RecentNewsCursorCodec codec = new RecentNewsCursorCodec(new ObjectMapper());

    @Test
    @DisplayName("열람 시각과 타이브레이크 id를 담아 라운드트립된다")
    void encodeDecode_roundTrips() {
        String encoded = codec.encode(VIEWED_AT, 101L);

        RecentNewsCursor decoded = codec.decode(encoded);

        assertThat(decoded.viewedAt()).isEqualTo(VIEWED_AT);
        assertThat(decoded.id()).isEqualTo(101L);
    }

    @Test
    @DisplayName("형식이 깨진 커서는 MYPAGE_400_004로 거부한다")
    void decode_rejectsMalformedCursor() {
        assertThatThrownBy(() -> codec.decode("!!not-base64-json!!"))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
    }

    @Test
    @DisplayName("열람 시각 키가 없는 조작 커서는 MYPAGE_400_004로 거부한다")
    void decode_rejectsCursorWithoutTimeKey() {
        String tampered = base64Url("{\"id\":55}");

        assertThatThrownBy(() -> codec.decode(tampered))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
    }

    @Test
    @DisplayName("타이브레이크 id가 없거나 양수가 아닌 조작 커서는 MYPAGE_400_004로 거부한다")
    void decode_rejectsCursorWithoutPositiveId() {
        String missingId = base64Url("{\"t\":\"2026-05-08T20:15:00\"}");
        String nonPositiveId = base64Url("{\"t\":\"2026-05-08T20:15:00\",\"id\":0}");

        assertThatThrownBy(() -> codec.decode(missingId))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
        assertThatThrownBy(() -> codec.decode(nonPositiveId))
                .isInstanceOf(MypageException.class)
                .extracting("errorCode")
                .isEqualTo(MypageErrorCode.INVALID_PAGE_REQUEST);
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
