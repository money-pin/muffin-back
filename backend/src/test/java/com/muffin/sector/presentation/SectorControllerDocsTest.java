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
import com.muffin.sector.presentation.dto.SectorListResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorGroupResponse;
import com.muffin.sector.presentation.dto.SectorListResponse.SectorResponse;
import java.util.List;
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
