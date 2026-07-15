package com.muffin.sector.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.sector.application.SectorQueryService;
import com.muffin.sector.presentation.dto.SectorListResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorGroupResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

@ExtendWith(MockitoExtension.class)
class SectorControllerTest {

    @Mock
    private SectorQueryService sectorQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new SectorController(sectorQueryService)).build();
    }

    @Test
    @DisplayName("투자 가능 섹터 목록을 공통 응답으로 반환한다")
    void getSectors_returnsAvailableSectors() throws Exception {
        SectorResponse sector = new SectorResponse("GOLD", "금", 1);
        SectorGroupResponse group = new SectorGroupResponse("BASE_ASSET", "기초자산", 1, List.of(sector));
        when(sectorQueryService.getAvailableSectors()).thenReturn(new SectorListResponse(100_000L, List.of(group)));

        mockMvc.perform(get("/api/sectors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.unitAmount").value(100000))
                .andExpect(jsonPath("$.result.groups[0].groupCode").value("BASE_ASSET"))
                .andExpect(jsonPath("$.result.groups[0].sectors[0].sectorCode").value("GOLD"));
    }
}
