package com.muffin.mypage.infrastructure;

import com.muffin.mypage.application.ScrapCursor;
import com.muffin.mypage.application.ScrapListQueryRepository;
import com.muffin.mypage.application.ScrapListRow;
import com.muffin.mypage.domain.ScrapSort;
import com.muffin.news.domain.category.QCategory;
import com.muffin.news.domain.news.QNews;
import com.muffin.scrap.domain.QScrap;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScrapListQueryRepositoryImpl implements ScrapListQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ScrapListRow> findScrapPage(Long userId, ScrapSort sort, ScrapCursor cursor, int limit) {
        QScrap scrap = QScrap.scrap;
        QNews news = QNews.news;
        QCategory category = QCategory.category;

        BooleanBuilder where = new BooleanBuilder().and(scrap.userId.eq(userId)).and(news.deletedAt.isNull());
        applyCursor(where, sort, cursor, scrap, news);

        return queryFactory
                .select(Projections.constructor(
                        ScrapListRow.class,
                        news.id,
                        news.title,
                        category.name,
                        news.thumbnailUrl,
                        news.viewCount,
                        news.publishedAt,
                        scrap.createdAt,
                        scrap.id))
                .from(scrap)
                .join(news)
                .on(news.id.eq(scrap.newsId))
                .join(category)
                .on(category.id.eq(news.categoryId))
                .where(where)
                .orderBy(orderBy(sort, scrap, news))
                .limit(limit)
                .fetch();
    }

    private void applyCursor(BooleanBuilder where, ScrapSort sort, ScrapCursor cursor, QScrap scrap, QNews news) {
        if (cursor == null) {
            return;
        }
        switch (sort) {
            case SAVED_DESC ->
                where.and(scrap.createdAt
                        .lt(cursor.timeKey())
                        .or(scrap.createdAt.eq(cursor.timeKey()).and(scrap.id.lt(cursor.id()))));
            case PUBLISHED_DESC ->
                where.and(news.publishedAt
                        .lt(cursor.timeKey())
                        .or(news.publishedAt.eq(cursor.timeKey()).and(news.id.lt(cursor.id()))));
            case VIEW_DESC ->
                where.and(news.viewCount
                        .lt(cursor.numberKey())
                        .or(news.viewCount.eq(cursor.numberKey()).and(news.id.lt(cursor.id()))));
        }
    }

    private OrderSpecifier<?>[] orderBy(ScrapSort sort, QScrap scrap, QNews news) {
        return switch (sort) {
            case SAVED_DESC -> new OrderSpecifier<?>[] {scrap.createdAt.desc(), scrap.id.desc()};
            case PUBLISHED_DESC -> new OrderSpecifier<?>[] {news.publishedAt.desc(), news.id.desc()};
            case VIEW_DESC -> new OrderSpecifier<?>[] {news.viewCount.desc(), news.id.desc()};
        };
    }
}
