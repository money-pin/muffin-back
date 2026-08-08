package com.muffin.mypage.presentation.home.dto;

import com.muffin.character.domain.enums.MuffinType;
import java.time.LocalDateTime;
import java.util.List;

public record MypageHomeResponse(
        String nickname, CharacterSummary character, StreakSummary streak, List<RecentNewsItem> recentNews) {

    public record CharacterSummary(
            Long characterId, MuffinType characterType, String characterName, String characterImageUrl) {}

    public record StreakSummary(int currentStreak, int maxStreak, List<WeeklyActivityDay> weeklyActivity) {}

    public record WeeklyActivityDay(WeekDay day, boolean participated) {}

    public record RecentNewsItem(Long newsId, String title, String thumbnailUrl, LocalDateTime readAt) {}
}
