package com.muffin.notification.domain;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_setting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class NotificationSettings extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "quiz_push_enabled", nullable = false)
    private boolean quizPushEnabled;

    @Column(name = "investment_result_push_enabled", nullable = false)
    private boolean investmentResultPushEnabled;

    @Column(name = "ranking_changed_push_enabled", nullable = false)
    private boolean rankingChangedPushEnabled;

    @Column(name = "news_update_push_enabled", nullable = false)
    private boolean newsUpdatePushEnabled;

    private NotificationSettings(Long userId) {
        if (userId == null) {
            throw new NullPointerException("userId는 필수입니다.");
        }
        this.userId = userId;
        this.quizPushEnabled = true;
        this.investmentResultPushEnabled = true;
        this.rankingChangedPushEnabled = true;
        this.newsUpdatePushEnabled = true;
    }

    // 회원가입 시 기본값(ON)으로 자동 생성
    public static NotificationSettings createDefault(Long userId) {
        return new NotificationSettings(userId);
    }

    // 사용자의 알림 설정 수정
    public void updateQuizPush(boolean enabled) {
        this.quizPushEnabled = enabled;
    }

    public void updateInvestmentResultPush(boolean enabled) {
        this.investmentResultPushEnabled = enabled;
    }

    public void updateRankingChangedPush(boolean enabled) {
        this.rankingChangedPushEnabled = enabled;
    }

    public void updateNewsUpdatePush(boolean enabled) {
        this.newsUpdatePushEnabled = enabled;
    }
}
