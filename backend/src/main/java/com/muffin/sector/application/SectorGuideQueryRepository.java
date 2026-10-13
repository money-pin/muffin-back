package com.muffin.sector.application;

import com.muffin.sector.application.projection.SectorGuideProjection;
import java.util.List;

/** 활성 섹터와 실제 기준 자산을 그룹·섹터 표시 순서대로 조회하는 읽기 전용 포트. */
public interface SectorGuideQueryRepository {

    List<SectorGuideProjection> findActiveSectorGuides();
}
