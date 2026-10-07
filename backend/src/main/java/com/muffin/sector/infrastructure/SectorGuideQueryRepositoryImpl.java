package com.muffin.sector.infrastructure;

import com.muffin.sector.application.SectorGuideQueryRepository;
import com.muffin.sector.application.projection.SectorGuideProjection;
import com.muffin.sector.domain.etf.QEtf;
import com.muffin.sector.domain.sector.QSector;
import com.muffin.sector.domain.sectorgroup.QSectorGroup;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SectorGuideQueryRepositoryImpl implements SectorGuideQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<SectorGuideProjection> findActiveSectorGuides() {
        QSector sector = QSector.sector;
        QSectorGroup group = QSectorGroup.sectorGroup;
        QEtf etf = QEtf.etf;

        return queryFactory
                .select(Projections.constructor(
                        SectorGuideProjection.class,
                        sector.sectorCode,
                        sector.name,
                        sector.description,
                        etf.etfCode,
                        etf.etfName,
                        etf.description))
                .from(sector)
                .join(group)
                .on(group.id.eq(sector.sectorGroupId))
                .leftJoin(etf)
                .on(etf.id.eq(sector.etfId))
                .where(sector.isActive.isTrue())
                .orderBy(group.groupOrder.asc(), sector.sectorOrder.asc(), sector.sectorCode.asc())
                .fetch();
    }
}
