package com.muffin.sector.application;

import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
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
