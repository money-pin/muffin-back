package com.muffin.mypage.application;

import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 최근 읽은 뉴스 목록 커서를 base64url(JSON)로 인코딩/디코딩한다.
 *
 * <p>커서는 클라이언트에게 불투명한 토큰이며, 형식이 조금이라도 어긋나거나 기준 키(열람 시각)가 비면 {@code MYPAGE_400_004}로 응답한다. 열람 시각이
 * 없으면 저장소가 {@code lt(null)} 비교식을 만들게 되므로, 조작된 커서를 디코딩 단계에서 미리 거부한다.
 */
@Component
@RequiredArgsConstructor
public class RecentNewsCursorCodec {

    private static final String INVALID_CURSOR_MESSAGE = "유효하지 않은 최근 읽은 뉴스 조회 커서입니다.";
    private final ObjectMapper objectMapper;

    /** 커서 기준값을 문자열로 인코딩한다. */
    public String encode(LocalDateTime viewedAt, long id) {
        CursorPayload payload = new CursorPayload(viewedAt == null ? null : viewedAt.toString(), id);
        String json = objectMapper.writeValueAsString(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서 문자열을 기준값으로 디코딩한다. 형식이 올바르지 않으면 {@link MypageException}을 던진다. */
    public RecentNewsCursor decode(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload =
                    objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), CursorPayload.class);
            if (payload.t() == null) {
                throw invalidCursor();
            }
            return new RecentNewsCursor(LocalDateTime.parse(payload.t()), payload.id());
        } catch (MypageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private MypageException invalidCursor() {
        return new MypageException(MypageErrorCode.INVALID_PAGE_REQUEST, INVALID_CURSOR_MESSAGE);
    }

    private record CursorPayload(String t, long id) {}
}
