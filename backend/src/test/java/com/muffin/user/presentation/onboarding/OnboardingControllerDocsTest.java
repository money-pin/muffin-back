package com.muffin.user.presentation.onboarding;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.user.application.onboarding.CharacterResultCommandService;
import com.muffin.user.application.onboarding.OnboardingCompletionService;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import com.muffin.user.presentation.onboarding.dto.CharacterResultRequest;
import com.muffin.user.presentation.onboarding.dto.CharacterResultResponse;
import com.muffin.user.presentation.onboarding.dto.OnboardingCompleteResponse;
import com.muffin.user.presentation.onboarding.dto.RecommendedSectorResponse;
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

/** 온보딩(캐릭터 결과 저장 / 완료+초기자산 지급) API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class OnboardingControllerDocsTest {

    private static final Long USER_ID = 1L;

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
    @DisplayName("캐릭터 결과 저장 성공 문서화")
    void documentCharacterResultSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        CharacterResultCommandService stub = new CharacterResultCommandService(null, null, null, null, null) {
            @Override
            public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
                return new CharacterResultResponse(
                        1L,
                        "PLAIN",
                        "플레인 머핀",
                        "기본에 충실한 안정형 캐릭터",
                        "https://example.com/plain.png",
                        List.of(
                                new RecommendedSectorResponse("SEMICONDUCTOR", "반도체"),
                                new RecommendedSectorResponse("GOLD", "금")));
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, stubCompletionService(), restDocumentation);

        mockMvc.perform(post("/api/onboarding/character")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "onboarding-character-result-success",
                        requestFields(
                                fieldWithPath("muffin").description("온보딩 설문으로 결정된 muffin 타입: plain/sprinkle/butter"),
                                fieldWithPath("firstQuestion").description("설문 1번 응답(1~3)"),
                                fieldWithPath("secondQuestion").description("설문 2번 응답(1~3)"),
                                fieldWithPath("thirdQuestion").description("설문 3번 응답(1~3)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.characterId").description("확정된 캐릭터 ID"),
                                fieldWithPath("result.characterType").description("캐릭터 타입(PLAIN/SPRINKLE/BUTTER)"),
                                fieldWithPath("result.characterName").description("캐릭터 이름"),
                                fieldWithPath("result.characterDescription").description("캐릭터 설명"),
                                fieldWithPath("result.imageUrl").description("캐릭터 이미지 URL"),
                                fieldWithPath("result.recommendedSectors[].sectorCode")
                                        .description("추천 섹터 코드"),
                                fieldWithPath("result.recommendedSectors[].sectorName")
                                        .description("추천 섹터 이름"))));
    }

    @Test
    @DisplayName("캐릭터 결과 재제출(이미 온보딩 완료) 문서화")
    void documentCharacterResultAlreadyCompleted(RestDocumentationContextProvider restDocumentation) throws Exception {
        CharacterResultCommandService stub = new CharacterResultCommandService(null, null, null, null, null) {
            @Override
            public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
                throw new IllegalStateException("이미 온보딩을 완료한 사용자입니다.");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, stubCompletionService(), restDocumentation);

        mockMvc.perform(post("/api/onboarding/character")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "onboarding-character-result-already-completed",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(COMMON_409_001)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("존재하지 않는 캐릭터 문서화")
    void documentCharacterResultCharacterNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        CharacterResultCommandService stub = new CharacterResultCommandService(null, null, null, null, null) {
            @Override
            public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
                throw new UserException(UserErrorCode.CHARACTER_NOT_FOUND);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, stubCompletionService(), restDocumentation);

        mockMvc.perform(post("/api/onboarding/character")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "onboarding-character-result-character-not-found",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(USER_404_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("온보딩 완료 및 초기자산 지급 성공 문서화")
    void documentCompleteSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        OnboardingCompletionService stub = new OnboardingCompletionService(null, null, null) {
            @Override
            public OnboardingCompleteResponse complete(Long userId) {
                return new OnboardingCompleteResponse(1_000_000L);
            }
        };
        MockMvc mockMvc = mockMvcOf(stubCharacterResultService(), stub, restDocumentation);

        mockMvc.perform(post("/api/onboarding/complete"))
                .andExpect(status().isOk())
                .andDo(document(
                        "onboarding-complete-success",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.totalAsset").description("총자산(신규 지급 또는 기존 자산 그대로)"))));
    }

    @Test
    @DisplayName("온보딩 미완료 상태에서 완료 요청 문서화")
    void documentCompleteOnboardingNotCompleted(RestDocumentationContextProvider restDocumentation) throws Exception {
        OnboardingCompletionService stub = new OnboardingCompletionService(null, null, null) {
            @Override
            public OnboardingCompleteResponse complete(Long userId) {
                throw new UserException(UserErrorCode.ONBOARDING_NOT_COMPLETED);
            }
        };
        MockMvc mockMvc = mockMvcOf(stubCharacterResultService(), stub, restDocumentation);

        mockMvc.perform(post("/api/onboarding/complete"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "onboarding-complete-not-completed",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(USER_409_001)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private CharacterResultCommandService stubCharacterResultService() {
        return new CharacterResultCommandService(null, null, null, null, null) {
            @Override
            public CharacterResultResponse submit(Long userId, CharacterResultRequest request) {
                throw new UnsupportedOperationException("이 테스트에서는 사용하지 않음");
            }
        };
    }

    private OnboardingCompletionService stubCompletionService() {
        return new OnboardingCompletionService(null, null, null) {
            @Override
            public OnboardingCompleteResponse complete(Long userId) {
                throw new UnsupportedOperationException("이 테스트에서는 사용하지 않음");
            }
        };
    }

    private MockMvc mockMvcOf(
            CharacterResultCommandService characterResultCommandService,
            OnboardingCompletionService onboardingCompletionService,
            RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(
                        new OnboardingController(characterResultCommandService, onboardingCompletionService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
