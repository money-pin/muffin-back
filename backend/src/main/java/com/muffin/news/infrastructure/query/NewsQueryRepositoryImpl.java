package com.muffin.news.infrastructure.query;

import com.muffin.news.application.query.NewsCursor;
import com.muffin.news.application.query.NewsQueryRepository;
import com.muffin.news.application.query.NewsSummaryRow;
import com.muffin.news.application.query.RecentReadNewsRow;
import com.muffin.news.application.sectorimpact.SectorRow;
import com.muffin.news.domain.category.QCategory;
import com.muffin.news.domain.news.QNews;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.readhistory.QReadHistory;
import com.muffin.sector.domain.sector.QSector;
import com.muffin.sector.domain.sectorgroup.QSectorGroup;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NewsQueryRepositoryImpl implements NewsQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<NewsSummaryRow> findPublishedNewsPage(NewsCursor cursor, Long categoryId, int limit) {
        QNews news = QNews.news;
        QCategory category = QCategory.category;

        BooleanBuilder where = publishedNewsPredicate(news);
        if (categoryId != null) {
            where.and(news.categoryId.eq(categoryId));
        }
        if (cursor != null) {
            where.and(news.publishedAt
                    .lt(cursor.publishedAt())
                    .or(news.publishedAt.eq(cursor.publishedAt()).and(news.id.lt(cursor.newsId()))));
        }

        return queryFactory
                .select(summaryProjection(news, category))
                .from(news)
                .join(category)
                .on(category.id.eq(news.categoryId))
                .where(where)
                .orderBy(news.publishedAt.desc(), news.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<NewsSummaryRow> findTodayPublishedNews(
            LocalDateTime startInclusive, LocalDateTime endExclusive, int limit) {
        QNews news = QNews.news;
        QCategory category = QCategory.category;

        BooleanBuilder where = publishedNewsPredicate(news);
        where.and(news.createdAt.goe(startInclusive)).and(news.createdAt.lt(endExclusive));

        return queryFactory
                .select(summaryProjection(news, category))
                .from(news)
                .join(category)
                .on(category.id.eq(news.categoryId))
                .where(where)
                .orderBy(news.publishedAt.desc(), news.id.desc())
                .limit(limit)
                .fetch();
    }

    @Override
    public List<SectorRow> findActiveSectorsInDisplayOrder() {
        QSector sector = QSector.sector;
        QSectorGroup sectorGroup = QSectorGroup.sectorGroup;

        return queryFactory
                .select(Projections.constructor(SectorRow.class, sector.id, sector.sectorCode, sector.name))
                .from(sector)
                .join(sectorGroup)
                .on(sectorGroup.id.eq(sector.sectorGroupId))
                .where(sector.isActive.isTrue())
                .orderBy(sectorGroup.groupOrder.asc(), sector.sectorOrder.asc())
                .fetch();
    }

    @Override
    public List<RecentReadNewsRow> findRecentReadNews(Long userId, int limit) {
        QReadHistory readHistory = QReadHistory.readHistory;
        QNews news = QNews.news;

        return queryFactory
                .select(Projections.constructor(
                        RecentReadNewsRow.class, news.id, news.title, news.thumbnailUrl, readHistory.readAt))
                .from(readHistory)
                .join(news)
                .on(news.id.eq(readHistory.newsId))
                .where(readHistory.userId.eq(userId))
                .orderBy(readHistory.readAt.desc(), readHistory.id.desc())
                .limit(limit)
                .fetch();
    }

    private BooleanBuilder publishedNewsPredicate(QNews news) {
        return new BooleanBuilder().and(news.status.eq(NewsStatus.PUBLISHED)).and(news.deletedAt.isNull());
    }

    private com.querydsl.core.types.ConstructorExpression<NewsSummaryRow> summaryProjection(
            QNews news, QCategory category) {
        return Projections.constructor(
                NewsSummaryRow.class,
                news.id,
                news.categoryId,
                category.name,
                news.title,
                news.summary,
                news.publisher,
                news.publishedAt,
                news.thumbnailUrl,
                news.viewCount,
                category.fallbackThumbnailUrl);
    }
}
