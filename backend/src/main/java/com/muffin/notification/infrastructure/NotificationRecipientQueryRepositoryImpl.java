package com.muffin.notification.infrastructure;

import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.domain.QNotificationSettings;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.user.domain.QUser;
import com.muffin.user.domain.enums.UserStatus;
import com.querydsl.core.types.dsl.BooleanPath;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRecipientQueryRepositoryImpl implements NotificationRecipientQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Long> findRecipientUserIds(NotificationType type) {
        QUser user = QUser.user;
        QNotificationSettings settings = QNotificationSettings.notificationSettings;

        return queryFactory
                .select(user.userId)
                .from(user)
                .leftJoin(settings)
                .on(settings.userId.eq(user.userId))
                // 탈퇴·정지 계정에는 보내지 않는다.
                .where(
                        user.status.eq(UserStatus.ACTIVE),
                        settings.userId.isNull().or(toggleOf(settings, type).isTrue()))
                .orderBy(user.userId.asc())
                .fetch();
    }

    /** 알림 종류별로 확인할 설정 토글. 브리핑은 별도 토글 없이 뉴스 알림을 함께 쓴다. */
    private BooleanPath toggleOf(QNotificationSettings settings, NotificationType type) {
        return switch (type) {
            case BRIEFING, NEWS -> settings.newsUpdatePushEnabled;
            case QUIZ -> settings.quizPushEnabled;
            case INVESTMENT_RESULT -> settings.investmentResultPushEnabled;
            case RANKING -> settings.rankingChangedPushEnabled;
        };
    }
}
