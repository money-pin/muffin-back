package com.muffin.sector.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SectorGuideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private SectorGroupRepository groupRepository;

    @Autowired
    private EtfRepository etfRepository;

    @Test
    @DisplayName("섹터 안내 API는 인증이 없으면 401을 반환한다")
    void getSectorGuide_requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/sectors/guide"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }

    @Test
    @DisplayName("인증된 요청은 DB의 ETF와 BTC 매핑을 구분하고 비활성 섹터를 제외한다")
    void getSectorGuide_returnsActualMappings() throws Exception {
        SectorGroup group = groupRepository.save(SectorGroup.create("BASE_ASSET", "기초", null, 1));
        Etf gold = etfRepository.save(Etf.create("132030", "KODEX 골드선물(H)", "금 선물 기반 ETF"));
        Etf btc = etfRepository.save(Etf.create("BTC", "비트코인", "비트코인 시세 기반"));
        sectorRepository.save(Sector.create(group.getId(), gold.getId(), "금", "글로벌 금 선물 기반", "GOLD", 1));
        sectorRepository.save(Sector.create(group.getId(), btc.getId(), "코인", null, "CRYPTO", 2));
        Sector inactive = Sector.create(group.getId(), gold.getId(), "숨긴 섹터", null, "HIDDEN", 0);
        inactive.deactivate();
        sectorRepository.save(inactive);

        mockMvc.perform(get("/api/sectors/guide").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.totalCount").value(2))
                .andExpect(jsonPath("$.result.sectors.length()").value(2))
                .andExpect(jsonPath("$.result.sectors[0].sectorCode").value("GOLD"))
                .andExpect(jsonPath("$.result.sectors[0].name").value("금"))
                .andExpect(jsonPath("$.result.sectors[0].description").value("글로벌 금 선물 기반"))
                .andExpect(jsonPath("$.result.sectors[0].referenceAsset.type").value("ETF"))
                .andExpect(jsonPath("$.result.sectors[0].referenceAsset.code").value("132030"))
                .andExpect(jsonPath("$.result.sectors[0].referenceAsset.name").value("KODEX 골드선물(H)"))
                .andExpect(jsonPath("$.result.sectors[0].referenceAsset.description")
                        .value("금 선물 기반 ETF"))
                .andExpect(jsonPath("$.result.sectors[1].referenceAsset.type").value("CRYPTO"))
                .andExpect(jsonPath("$.result.sectors[1].referenceAsset.code").value("BTC"))
                .andExpect(jsonPath("$.result.sectors[1].referenceAsset.name").value("비트코인"))
                .andExpect(jsonPath("$.result.sectors[1].referenceAsset.description")
                        .value("비트코인 시세 기반"));
    }

    @Test
    @DisplayName("활성 섹터가 없으면 빈 안내 목록을 정상 반환한다")
    void getSectorGuide_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/sectors/guide").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalCount").value(0))
                .andExpect(jsonPath("$.result.sectors").isEmpty());
    }

    @Test
    @DisplayName("DB 기준 자산 연결이 누락되면 명시적인 서버 오류를 반환한다")
    void getSectorGuide_reportsMissingAsset() throws Exception {
        SectorGroup group = groupRepository.save(SectorGroup.create("BASE_ASSET", "기초", null, 1));
        sectorRepository.save(Sector.create(group.getId(), Long.MAX_VALUE, "금", null, "GOLD", 1));

        mockMvc.perform(get("/api/sectors/guide").header("Authorization", bearer()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SECTOR_500_001"));
    }

    private String bearer() {
        return "Bearer " + accessTokenProvider.issue(1L, "USER");
    }
}
