package com.muffin.sector.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.sector.application.SectorQueryService;
import com.muffin.sector.presentation.dto.SectorGuideResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse.ReferenceAssetResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse.ReferenceAssetType;
import com.muffin.sector.presentation.dto.SectorGuideResponse.SectorGuideItem;
import com.muffin.sector.presentation.dto.SectorListResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorGroupResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** INVEST-02 투자 가능 섹터 목록 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith({MockitoExtension.class, RestDocumentationExtension.class})
class SectorControllerDocsTest {

    private static final String AUTHORIZATION = "Bearer {accessToken}";

    @Mock
    private SectorQueryService sectorQueryService;

    @Test
    @DisplayName("섹터 안내의 ETF·BTC 응답과 nullable 설명을 문서화한다")
    void documentSectorGuide(RestDocumentationContextProvider restDocumentation) throws Exception {
        when(sectorQueryService.getSectorGuide())
                .thenReturn(new SectorGuideResponse(
                        2,
                        List.of(
                                new SectorGuideItem(
                                        "GOLD",
                                        "금",
                                        "글로벌 금 선물 기반",
                                        new ReferenceAssetResponse(ReferenceAssetType.ETF, "132030", "KODEX 골드선물(H)")),
                                new SectorGuideItem(
                                        "CRYPTO",
                                        "코인",
                                        null,
                                        new ReferenceAssetResponse(ReferenceAssetType.CRYPTO, "BTC", "비트코인")))));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SectorController(sectorQueryService))
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();

        mockMvc.perform(get("/api/sectors/guide").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sector-guide-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.totalCount").description("반환된 활성 섹터 수"),
                                fieldWithPath("result.sectors")
                                        .description("그룹 표시 순서, 섹터 표시 순서, 섹터 코드 오름차순의 활성 섹터 목록. 없으면 빈 배열"),
                                fieldWithPath("result.sectors[].sectorCode").description("섹터 코드. 프론트 아이콘 매핑에 사용"),
                                fieldWithPath("result.sectors[].name").description("섹터 이름"),
                                fieldWithPath("result.sectors[].description")
                                        .optional()
                                        .description("섹터 설명. 미등록 시 null"),
                                fieldWithPath("result.sectors[].referenceAsset")
                                        .description("DB에서 해당 섹터에 실제 연결된 기준 자산"),
                                fieldWithPath("result.sectors[].referenceAsset.type")
                                        .description("기준 자산 유형: ETF 또는 CRYPTO. BTC는 CRYPTO"),
                                fieldWithPath("result.sectors[].referenceAsset.code")
                                        .description("ETF 종목코드 또는 BTC"),
                                fieldWithPath("result.sectors[].referenceAsset.name")
                                        .description("기준 자산 이름"))));
    }

    @Test
    void documentAvailableSectors(RestDocumentationContextProvider restDocumentation) throws Exception {
        SectorListResponse response = new SectorListResponse(
                100_000L,
                List.of(new SectorGroupResponse(
                        "BASE_ASSET",
                        "기초 자산",
                        1,
                        List.of(new SectorResponse("GOLD", "금", 1), new SectorResponse("USD", "달러", 2)))));
        when(sectorQueryService.getAvailableSectors()).thenReturn(response);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SectorController(sectorQueryService))
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();

        mockMvc.perform(get("/api/sectors").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "sector-list-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.unitAmount").description("수량 1단위의 투자 금액"),
                                fieldWithPath("result.groups").description("섹터 그룹 목록. groupOrder 오름차순"),
                                fieldWithPath("result.groups[].groupCode").description("섹터 그룹 코드"),
                                fieldWithPath("result.groups[].groupName").description("섹터 그룹 이름"),
                                fieldWithPath("result.groups[].groupOrder").description("섹터 그룹 노출 순서"),
                                fieldWithPath("result.groups[].sectors").description("활성화된 섹터 목록. sectorOrder 오름차순"),
                                fieldWithPath("result.groups[].sectors[].sectorCode")
                                        .description("섹터 코드"),
                                fieldWithPath("result.groups[].sectors[].name").description("섹터 이름"),
                                fieldWithPath("result.groups[].sectors[].sectorOrder")
                                        .description("섹터 노출 순서"))));
    }
}
