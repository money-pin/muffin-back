package com.muffin.mypage.presentation.home;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.mypage.application.home.MypageHomeQueryService;
import com.muffin.mypage.presentation.MypageController;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.CharacterSummary;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.RecentNewsItem;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.StreakSummary;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.WeeklyActivityDay;
import com.muffin.mypage.presentation.home.dto.WeekDay;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 마이페이지 홈 조회 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class MypageHomeControllerDocsTest {

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
    @DisplayName("마이페이지 홈 조회 문서화")
    void documentGetHome(RestDocumentationContextProvider restDocumentation) throws Exception {
        MypageHomeQueryService stub = new MypageHomeQueryService(null, null, null, null, null) {
            @Override
            public MypageHomeResponse getHome(Long userId) {
                List<WeeklyActivityDay> weeklyActivity = List.of(
                        new WeeklyActivityDay(WeekDay.SUN, false),
                        new WeeklyActivityDay(WeekDay.MON, true),
                        new WeeklyActivityDay(WeekDay.TUE, true),
                        new WeeklyActivityDay(WeekDay.WED, true),
                        new WeeklyActivityDay(WeekDay.THU, true),
                        new WeeklyActivityDay(WeekDay.FRI, true),
                        new WeeklyActivityDay(WeekDay.SAT, false));
                return new MypageHomeResponse(
                        "윤성",
                        new CharacterSummary(
                                1L, MuffinType.PLAIN, "플레인 머핀", "https://example.com/images/characters/plain.png"),
                        new StreakSummary(5, 12, weeklyActivity),
                        List.of(
                                new RecentNewsItem(
                                        1L,
                                        "한국은행, 기준금리 동결 결정",
                                        "https://example.com/images/news/1.jpg",
                                        LocalDateTime.now()),
                                new RecentNewsItem(
                                        2L,
                                        "미국 기술주 상승세 지속",
                                        "https://example.com/images/news/2.jpg",
                                        LocalDateTime.now())));
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(get("/api/mypage/home"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-home-get",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.nickname").description("닉네임"),
                                fieldWithPath("result.character.characterId").description("캐릭터 ID"),
                                fieldWithPath("result.character.characterType")
                                        .description("캐릭터 타입(PLAIN/SPRINKLE/BUTTER)"),
                                fieldWithPath("result.character.characterName").description("캐릭터명"),
                                fieldWithPath("result.character.characterImageUrl")
                                        .description("캐릭터 이미지 URL"),
                                fieldWithPath("result.streak.currentStreak").description("현재 연속 참여일"),
                                fieldWithPath("result.streak.maxStreak").description("최장 연속 참여일"),
                                fieldWithPath("result.streak.weeklyActivity[].day")
                                        .description("요일(SUN~SAT)"),
                                fieldWithPath("result.streak.weeklyActivity[].participated")
                                        .description("해당 요일 참여 여부"),
                                fieldWithPath("result.recentNews[].newsId").description("뉴스 ID"),
                                fieldWithPath("result.recentNews[].title").description("뉴스 제목"),
                                fieldWithPath("result.recentNews[].thumbnailUrl")
                                        .description("썸네일 URL"),
                                fieldWithPath("result.recentNews[].readAt").description("열람 시각"))));
    }

    private MockMvc mockMvcOf(MypageHomeQueryService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new MypageController(null, null, stub, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
