package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import com.muffin.sector.presentation.dto.SectorListResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SectorQueryServiceTest {

    @Mock
    private SectorGroupRepository sectorGroupRepository;

    @Mock
    private SectorRepository sectorRepository;

    private SectorQueryService sectorQueryService;

    @BeforeEach
    void setUp() {
        sectorQueryService = new SectorQueryService(sectorGroupRepository, sectorRepository);
    }

    @Test
    @DisplayName("활성 섹터만 그룹 표시 순서에 맞춰 응답한다")
    void getAvailableSectors_groupsActiveSectors() {
        SectorGroup baseAsset = group(1L, "BASE_ASSET", "기초자산", 1);
        SectorGroup futureTech = group(2L, "FUTURE_TECH", "미래 기술&혁신", 2);
        Sector gold = Sector.create(1L, 101L, "금", "", "GOLD", 2);
        Sector deposit = Sector.create(1L, 102L, "예금", "", "DEPOSIT", 1);
        Sector semiconductor = Sector.create(2L, 103L, "반도체", "", "SEMICONDUCTOR", 1);

        when(sectorGroupRepository.findAllByOrderByGroupOrderAsc()).thenReturn(List.of(baseAsset, futureTech));
        when(sectorRepository.findByIsActiveTrueOrderBySectorOrderAsc())
                .thenReturn(List.of(deposit, semiconductor, gold));

        SectorListResponse response = sectorQueryService.getAvailableSectors();

        assertEquals(100_000L, response.unitAmount());
        assertEquals(
                List.of("BASE_ASSET", "FUTURE_TECH"),
                response.groups().stream()
                        .map(SectorListResponse.SectorGroupResponse::groupCode)
                        .toList());
        assertEquals(
                List.of("DEPOSIT", "GOLD"),
                response.groups().getFirst().sectors().stream()
                        .map(SectorListResponse.SectorResponse::sectorCode)
                        .toList());
    }

    private static SectorGroup group(Long id, String code, String name, int order) {
        SectorGroup group = SectorGroup.create(code, name, "", order);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }
}
