package com.muffin.news.application.query;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 뉴스 목록 커서를 base64url(JSON)로 인코딩/디코딩한다.
 *
 * <p>커서는 클라이언트에게 불투명한 토큰이며, 형식이 조금이라도 어긋나면 {@code COMMON_400_001}로 응답한다.
 */
@Component
@RequiredArgsConstructor
public class NewsCursorCodec {

    private static final String INVALID_CURSOR_MESSAGE = "유효하지 않은 뉴스 조회 커서입니다.";
    private final ObjectMapper objectMapper;

    /** 정렬 기준값을 커서 문자열로 인코딩한다. */
    public String encode(LocalDateTime publishedAt, Long newsId) {
        String json = objectMapper.writeValueAsString(new CursorPayload(publishedAt, newsId));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서 문자열을 정렬 기준값으로 디코딩한다. 형식이 올바르지 않으면 {@link GeneralException}을 던진다. */
    public NewsCursor decode(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload =
                    objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), CursorPayload.class);
            if (payload.publishedAt() == null || payload.newsId() == null) {
                throw new GeneralException(GeneralErrorCode.BAD_REQUEST, INVALID_CURSOR_MESSAGE);
            }
            return new NewsCursor(payload.publishedAt(), payload.newsId());
        } catch (GeneralException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST, INVALID_CURSOR_MESSAGE);
        }
    }

    private record CursorPayload(LocalDateTime publishedAt, Long newsId) {}
}
