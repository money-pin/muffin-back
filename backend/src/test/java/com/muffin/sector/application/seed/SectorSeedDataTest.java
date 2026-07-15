package com.muffin.sector.application.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.sector.application.seed.SectorSeedData.EtfSeed;
import com.muffin.sector.application.seed.SectorSeedData.GroupSeed;
import com.muffin.sector.application.seed.SectorSeedData.SectorSeed;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SectorSeedDataTest {

    @Test
    @DisplayName("CRYPTO를 제외한 11개 섹터가 정의되어 있다")
    void sectors_excludeCrypto() {
        assertEquals(11, SectorSeedData.SECTORS.size());
        assertTrue(SectorSeedData.SECTORS.stream()
                .noneMatch(sector -> sector.sectorCode().equals("CRYPTO")));
    }

    @Test
    @DisplayName("모든 섹터 코드는 유일하다")
    void sectorCodes_areUnique() {
        Set<String> sectorCodes =
                SectorSeedData.SECTORS.stream().map(SectorSeed::sectorCode).collect(Collectors.toSet());

        assertEquals(SectorSeedData.SECTORS.size(), sectorCodes.size());
    }

    @Test
    @DisplayName("11개 ETF가 정의되어 있고 종목코드가 유일하다")
    void etfs_areUniqueAndComplete() {
        assertEquals(11, SectorSeedData.ETFS.size());
        Set<String> etfCodes =
                SectorSeedData.ETFS.stream().map(EtfSeed::etfCode).collect(Collectors.toSet());
        assertEquals(SectorSeedData.ETFS.size(), etfCodes.size());
    }

    @Test
    @DisplayName("3개 섹터 그룹이 정의되어 있고 그룹 코드가 유일하다")
    void groups_areUniqueAndComplete() {
        assertEquals(3, SectorSeedData.GROUPS.size());
        Set<String> groupCodes =
                SectorSeedData.GROUPS.stream().map(GroupSeed::groupCode).collect(Collectors.toSet());
        assertEquals(SectorSeedData.GROUPS.size(), groupCodes.size());
    }

    @Test
    @DisplayName("모든 섹터는 실제 존재하는 그룹 코드와 ETF 코드를 참조한다")
    void sectors_referenceExistingGroupsAndEtfs() {
        Set<String> groupCodes =
                SectorSeedData.GROUPS.stream().map(GroupSeed::groupCode).collect(Collectors.toSet());
        Set<String> etfCodes =
                SectorSeedData.ETFS.stream().map(EtfSeed::etfCode).collect(Collectors.toSet());

        for (SectorSeed sector : SectorSeedData.SECTORS) {
            assertTrue(groupCodes.contains(sector.groupCode()), sector.sectorCode() + "의 그룹 코드가 존재하지 않습니다");
            assertTrue(etfCodes.contains(sector.etfCode()), sector.sectorCode() + "의 ETF 코드가 존재하지 않습니다");
        }
    }
}
