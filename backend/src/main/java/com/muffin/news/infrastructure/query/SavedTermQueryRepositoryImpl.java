package com.muffin.news.infrastructure.query;

import com.muffin.news.application.term.SavedTermQueryRepository;
import com.muffin.news.application.term.SavedTermRow;
import com.muffin.news.domain.term.QTermDictionary;
import com.muffin.news.domain.term.QUserSavedTerm;
import com.muffin.news.domain.term.SavedTermSort;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SavedTermQueryRepositoryImpl implements SavedTermQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public Slice<SavedTermRow> findSavedTerms(Long userId, SavedTermSort sort, Pageable pageable) {
        QUserSavedTerm userSavedTerm = QUserSavedTerm.userSavedTerm;
        QTermDictionary termDictionary = QTermDictionary.termDictionary;

        List<SavedTermRow> rows = queryFactory
                .select(Projections.constructor(
                        SavedTermRow.class,
                        termDictionary.id,
                        termDictionary.term,
                        termDictionary.content,
                        userSavedTerm.savedAt))
                .from(userSavedTerm)
                .join(termDictionary)
                .on(termDictionary.id.eq(userSavedTerm.termId))
                .where(userSavedTerm.userId.eq(userId))
                .orderBy(orderSpecifiers(sort, userSavedTerm, termDictionary))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize() + 1L)
                .fetch();

        boolean hasNext = rows.size() > pageable.getPageSize();
        List<SavedTermRow> content = hasNext ? rows.subList(0, pageable.getPageSize()) : rows;
        return new SliceImpl<>(content, pageable, hasNext);
    }

    private OrderSpecifier<?>[] orderSpecifiers(
            SavedTermSort sort, QUserSavedTerm userSavedTerm, QTermDictionary termDictionary) {
        return switch (sort) {
            case ALPHABETICAL -> new OrderSpecifier<?>[] {termDictionary.term.asc(), userSavedTerm.id.asc()};
            case RECENT -> new OrderSpecifier<?>[] {userSavedTerm.savedAt.desc(), userSavedTerm.id.desc()};
        };
    }
}
