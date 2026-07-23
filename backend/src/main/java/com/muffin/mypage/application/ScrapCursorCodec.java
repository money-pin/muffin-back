package com.muffin.mypage.application;

import com.muffin.mypage.domain.ScrapSort;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 스크랩 목록 커서를 base64url(JSON)로 인코딩/디코딩한다.
 *
 * <p>커서는 클라이언트에게 불투명한 토큰이며, 형식이 조금이라도 어긋나면 {@code MYPAGE_400_004}로 응답한다. 정렬 종류(sort)도 커서에 담아,
 * 페이지네이션 도중 정렬을 바꾸면 서비스가 이를 감지해 거부할 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class ScrapCursorCodec {

    private static final String INVALID_CURSOR_MESSAGE = "유효하지 않은 스크랩 조회 커서입니다.";
    private final ObjectMapper objectMapper;

    /** 정렬 기준값을 커서 문자열로 인코딩한다. {@code timeKey}/{@code numberKey} 중 하나만 채워진다. */
    public String encode(ScrapSort sort, LocalDateTime timeKey, Long numberKey, long id) {
        CursorPayload payload =
                new CursorPayload(sort.name(), timeKey == null ? null : timeKey.toString(), numberKey, id);
        String json = objectMapper.writeValueAsString(payload);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /** 커서 문자열을 정렬 기준값으로 디코딩한다. 형식이 올바르지 않으면 {@link MypageException}을 던진다. */
    public ScrapCursor decode(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload =
                    objectMapper.readValue(new String(decoded, StandardCharsets.UTF_8), CursorPayload.class);
            ScrapSort sort = ScrapSort.valueOf(payload.s());
            LocalDateTime timeKey = payload.t() == null ? null : LocalDateTime.parse(payload.t());
            boolean hasTime = timeKey != null;
            boolean hasNumber = payload.n() != null;
            // 정렬별로 맞는 기준 키가 정확히 하나만 있어야 한다. 시간 정렬엔 timeKey만, 조회수 정렬엔 numberKey만
            // 허용한다(반대 키/둘 다/둘 다 없음은 변조로 보고 거부해, 저장소가 null 비교식을 만들지 않게 한다).
            boolean validKey =
                    switch (sort) {
                        case SAVED_DESC, PUBLISHED_DESC -> hasTime && !hasNumber;
                        case VIEW_DESC -> !hasTime && hasNumber;
                    };
            if (!validKey) {
                throw invalidCursor();
            }
            return new ScrapCursor(sort, timeKey, payload.n(), payload.id());
        } catch (MypageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private MypageException invalidCursor() {
        return new MypageException(MypageErrorCode.INVALID_PAGE_REQUEST, INVALID_CURSOR_MESSAGE);
    }

    private record CursorPayload(String s, String t, Long n, long id) {}
}
