package com.muffin.user.presentation.nickname;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.user.application.nickname.NicknameCommandService;
import com.muffin.user.application.nickname.NicknameQueryService;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import com.muffin.user.presentation.nickname.dto.NicknameChangeRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/** 닉네임 조회/변경 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class NicknameControllerDocsTest {

    private static final Long USER_ID = 1L;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("사용 가능한 닉네임 조회 문서화")
    void documentAvailable(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameQueryService stub = new NicknameQueryService(null, null) {
            @Override
            public boolean isAvailable(String nickname) {
                return true;
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, null, restDocumentation);

        mockMvc.perform(get("/api/mypage/nickname/check").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andDo(document(
                        "nickname-check-available",
                        queryParameters(parameterWithName("nickname").description("중복 확인할 닉네임")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.available").description("사용 가능 여부(true=사용 가능)"))));
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임 조회 문서화")
    void documentAlreadyTaken(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameQueryService stub = new NicknameQueryService(null, null) {
            @Override
            public boolean isAvailable(String nickname) {
                return false;
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, null, restDocumentation);

        mockMvc.perform(get("/api/mypage/nickname/check").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andDo(document(
                        "nickname-check-taken",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.available").description("사용 가능 여부(false=이미 사용 중)"))));
    }

    @Test
    @DisplayName("닉네임 변경 성공 문서화")
    void documentChangeSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameCommandService stub = new NicknameCommandService(null, null) {
            @Override
            public String changeNickname(Long userId, String newNickname) {
                return newNickname;
            }
        };
        MockMvc mockMvc = mockMvcOf(null, stub, restDocumentation);

        mockMvc.perform(patch("/api/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("새닉네임"))))
                .andExpect(status().isOk())
                .andDo(document(
                        "nickname-change-success",
                        requestFields(fieldWithPath("nickname").description("변경할 닉네임(2~6자)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.nickname").description("변경된 닉네임"))));
    }

    @Test
    @DisplayName("닉네임 변경 시 중복이면 409 문서화")
    void documentChangeDuplicated(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameCommandService stub = new NicknameCommandService(null, null) {
            @Override
            public String changeNickname(Long userId, String newNickname) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATED);
            }
        };
        MockMvc mockMvc = mockMvcOf(null, stub, restDocumentation);

        mockMvc.perform(patch("/api/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("중복닉네임"))))
                .andExpect(status().isConflict())
                .andDo(document(
                        "nickname-change-duplicated",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("실패 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(
            NicknameQueryService queryStub,
            NicknameCommandService commandStub,
            RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new NicknameController(queryStub, commandStub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
