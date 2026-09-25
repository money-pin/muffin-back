package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.muffin.sector.application.projection.SectorGuideProjection;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import com.muffin.sector.presentation.dto.SectorGuideResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse.ReferenceAssetType;
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

    @Mock
    private SectorGuideQueryRepository sectorGuideQueryRepository;

    private SectorQueryService sectorQueryService;

    @BeforeEach
    void setUp() {
        sectorQueryService =
                new SectorQueryService(sectorGroupRepository, sectorRepository, sectorGuideQueryRepository);
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

    @Test
    @DisplayName("안내는 실제 DB 상품 정보와 설명을 반환하고 BTC를 코인으로 구분한다")
    void getSectorGuide_mapsReferenceAssets() {
        when(sectorGuideQueryRepository.findActiveSectorGuides())
                .thenReturn(List.of(
                        new SectorGuideProjection("GOLD", "금", "DB 설명", "CUSTOM", "DB 상품명"),
                        new SectorGuideProjection("CRYPTO", "코인", null, "BTC", "비트코인")));

        SectorGuideResponse response = sectorQueryService.getSectorGuide();

        assertEquals(2, response.totalCount());
        assertEquals("GOLD", response.sectors().getFirst().sectorCode());
        assertEquals("금", response.sectors().getFirst().name());
        assertEquals("DB 설명", response.sectors().getFirst().description());
        assertEquals("CUSTOM", response.sectors().getFirst().referenceAsset().code());
        assertEquals("DB 상품명", response.sectors().getFirst().referenceAsset().name());
        assertEquals(
                ReferenceAssetType.ETF,
                response.sectors().getFirst().referenceAsset().type());
        assertEquals(
                ReferenceAssetType.CRYPTO,
                response.sectors().get(1).referenceAsset().type());
        assertNull(response.sectors().get(1).description());
    }

    @Test
    @DisplayName("활성 섹터가 없으면 안내 개수 0과 빈 목록을 반환한다")
    void getSectorGuide_returnsEmptyList() {
        when(sectorGuideQueryRepository.findActiveSectorGuides()).thenReturn(List.of());

        SectorGuideResponse response = sectorQueryService.getSectorGuide();

        assertEquals(0, response.totalCount());
        assertEquals(List.of(), response.sectors());
    }

    @Test
    @DisplayName("기준 자산 매핑이 없으면 불완전한 안내 대신 오류를 반환한다")
    void getSectorGuide_rejectsMissingAsset() {
        when(sectorGuideQueryRepository.findActiveSectorGuides())
                .thenReturn(List.of(new SectorGuideProjection("GOLD", "금", null, null, null)));

        SectorException exception = assertThrows(SectorException.class, () -> sectorQueryService.getSectorGuide());

        assertEquals(SectorErrorCode.REFERENCE_ASSET_NOT_FOUND, exception.getErrorCode());
    }

    private static SectorGroup group(Long id, String code, String name, int order) {
        SectorGroup group = SectorGroup.create(code, name, "", order);
        ReflectionTestUtils.setField(group, "id", id);
        return group;
    }
}
