package com.muffin.mypage.infrastructure;

import com.muffin.mypage.application.RecentNewsCursor;
import com.muffin.mypage.application.RecentNewsQueryRepository;
import com.muffin.mypage.application.projection.RecentNewsProjection;
import com.muffin.news.domain.category.QCategory;
import com.muffin.news.domain.news.QNews;
import com.muffin.news.domain.readhistory.QReadHistory;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RecentNewsQueryRepositoryImpl implements RecentNewsQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<RecentNewsProjection> findRecentNewsPage(Long userId, RecentNewsCursor cursor, int limit) {
        QReadHistory readHistory = QReadHistory.readHistory;
        QNews news = QNews.news;
        QCategory category = QCategory.category;

        BooleanBuilder where =
                new BooleanBuilder().and(readHistory.userId.eq(userId)).and(news.deletedAt.isNull());
        applyCursor(where, cursor, readHistory);

        return queryFactory
                .select(Projections.constructor(
                        RecentNewsProjection.class,
                        news.id,
                        news.title,
                        category.name,
                        news.thumbnailUrl,
                        news.viewCount,
                        news.publishedAt,
                        readHistory.readAt,
                        readHistory.id))
                .from(readHistory)
                .join(news)
                .on(news.id.eq(readHistory.newsId))
                .join(category)
                .on(category.id.eq(news.categoryId))
                .where(where)
                .orderBy(readHistory.readAt.desc(), readHistory.id.desc())
                .limit(limit)
                .fetch();
    }

    private void applyCursor(BooleanBuilder where, RecentNewsCursor cursor, QReadHistory readHistory) {
        if (cursor == null) {
            return;
        }
        where.and(readHistory
                .readAt
                .lt(cursor.viewedAt())
                .or(readHistory.readAt.eq(cursor.viewedAt()).and(readHistory.id.lt(cursor.id()))));
    }
}
