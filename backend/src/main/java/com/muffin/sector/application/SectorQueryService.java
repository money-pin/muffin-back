package com.muffin.sector.application;

import com.muffin.sector.application.projection.SectorGuideProjection;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import com.muffin.sector.presentation.dto.SectorGuideResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse.ReferenceAssetResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse.ReferenceAssetType;
import com.muffin.sector.presentation.dto.SectorGuideResponse.SectorGuideItem;
import com.muffin.sector.presentation.dto.SectorListResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorGroupResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SectorQueryService {

    public static final long UNIT_AMOUNT = 100_000L;

    private final SectorGroupRepository sectorGroupRepository;
    private final SectorRepository sectorRepository;
    private final SectorGuideQueryRepository sectorGuideQueryRepository;

    @Transactional(readOnly = true)
    public SectorGuideResponse getSectorGuide() {
        List<SectorGuideItem> sectors = sectorGuideQueryRepository.findActiveSectorGuides().stream()
                .map(this::toGuideItem)
                .toList();
        return new SectorGuideResponse(sectors.size(), sectors);
    }

    private SectorGuideItem toGuideItem(SectorGuideProjection sector) {
        if (sector.assetCode() == null || sector.assetName() == null) {
            throw new SectorException(SectorErrorCode.REFERENCE_ASSET_NOT_FOUND);
        }
        // 현재 코인 기준 자산은 ETF가 아닌 CoinGecko의 BTC 시세를 사용한다.
        ReferenceAssetType type = "BTC".equals(sector.assetCode()) ? ReferenceAssetType.CRYPTO : ReferenceAssetType.ETF;
        return new SectorGuideItem(
                sector.sectorCode(),
                sector.name(),
                sector.description(),
                new ReferenceAssetResponse(type, sector.assetCode(), sector.assetName()));
    }

    @Transactional(readOnly = true)
    public SectorListResponse getAvailableSectors() {
        Map<Long, List<Sector>> sectorsByGroup = sectorRepository.findByIsActiveTrueOrderBySectorOrderAsc().stream()
                .collect(Collectors.groupingBy(Sector::getSectorGroupId));

        List<SectorGroupResponse> groups = sectorGroupRepository.findAllByOrderByGroupOrderAsc().stream()
                .filter(group -> sectorsByGroup.containsKey(group.getId()))
                .map(group -> new SectorGroupResponse(
                        group.getGroupCode(),
                        group.getName(),
                        group.getGroupOrder(),
                        sectorsByGroup.getOrDefault(group.getId(), List.of()).stream()
                                .map(sector -> new SectorResponse(
                                        sector.getSectorCode(), sector.getName(), sector.getSectorOrder()))
                                .toList()))
                .toList();

        return new SectorListResponse(UNIT_AMOUNT, groups);
    }
}
