package com.muffin.mypage.presentation.home.dto;

import com.muffin.character.domain.enums.MuffinType;
import java.time.OffsetDateTime;
import java.util.List;

public record MypageHomeResponse(
        String nickname, CharacterSummary character, StreakSummary streak, List<RecentNewsItem> recentNews) {

    public record CharacterSummary(
            Long characterId, MuffinType characterType, String characterName, String characterImageUrl) {}

    public record StreakSummary(int currentStreak, int maxStreak, List<WeeklyActivityDay> weeklyActivity) {}

    public record WeeklyActivityDay(WeekDay day, boolean participated) {}

    /**
     * 홈에 띄우는 최근 읽은 뉴스 카드.
     *
     * <p>최근 읽은 뉴스 목록({@code GET /api/mypage/recent-news})과 같은 카드를 그리므로 필드 구성을 맞춘다. 한쪽에만
     * 값이 있으면 그 화면에서 조회수와 날짜가 빈 값으로 렌더링된다.
     *
     * @param publishedAt 발행 시각(KST)
     * @param viewedAt 열람 시각(KST)
     */
    public record RecentNewsItem(
            Long newsId,
            String title,
            String categoryName,
            String thumbnailUrl,
            Long viewCount,
            OffsetDateTime publishedAt,
            OffsetDateTime viewedAt,
            boolean isScrapped) {}
}
