package com.muffin.news.presentation;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTerm;
import com.muffin.news.domain.term.UserSavedTermRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 저장한 용어 목록 API가 실제 서비스/저장소/QueryDSL 조인/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SavedTermControllerTest {

    private static final Long USER_ID = 200L;
    private static final Long OTHER_USER_ID = 201L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private TermDictionaryRepository termDictionaryRepository;

    @Autowired
    private UserSavedTermRepository userSavedTermRepository;

    @AfterEach
    void cleanUp() {
        userSavedTermRepository.deleteAll();
        termDictionaryRepository.deleteAll();
    }

    private String bearerToken() {
        return "Bearer " + accessTokenProvider.issue(USER_ID, "USER");
    }

    private TermDictionary term(String term, String content) {
        return termDictionaryRepository.save(TermDictionary.create(term, content));
    }

    private void save(Long termId) {
        save(USER_ID, termId);
    }

    private void save(Long userId, Long termId) {
        userSavedTermRepository.save(UserSavedTerm.create(userId, termId));
    }

    @Test
    @DisplayName("sort=recent(기본값)이면 최근 저장한 순서대로 반환한다")
    void getSavedTerms_recentSort_ordersBySavedAtDesc() throws Exception {
        TermDictionary first = term("기준금리", "설명1");
        save(first.getId());
        TermDictionary second = term("ETF", "설명2");
        save(second.getId());

        mockMvc.perform(get("/api/mypage/saved-terms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.savedTerms.length()", is(2)))
                .andExpect(jsonPath("$.result.savedTerms[0].term", is("ETF")))
                .andExpect(jsonPath("$.result.savedTerms[1].term", is("기준금리")))
                .andExpect(jsonPath("$.result.page", is(0)))
                .andExpect(jsonPath("$.result.size", is(20)))
                .andExpect(jsonPath("$.result.hasNext", is(false)));
    }

    @Test
    @DisplayName("sort=alphabetical이면 가나다순으로 반환한다")
    void getSavedTerms_alphabeticalSort_ordersByTermAsc() throws Exception {
        TermDictionary zebra = term("환율", "설명1");
        save(zebra.getId());
        TermDictionary apple = term("금리", "설명2");
        save(apple.getId());
        TermDictionary middle = term("배당", "설명3");
        save(middle.getId());

        mockMvc.perform(get("/api/mypage/saved-terms")
                        .header("Authorization", bearerToken())
                        .param("sort", "alphabetical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.savedTerms[0].term", is("금리")))
                .andExpect(jsonPath("$.result.savedTerms[1].term", is("배당")))
                .andExpect(jsonPath("$.result.savedTerms[2].term", is("환율")));
    }

    @Test
    @DisplayName("size보다 저장한 용어가 많으면 hasNext=true, size만큼만 반환한다")
    void getSavedTerms_pagination_hasNext() throws Exception {
        for (int i = 0; i < 3; i++) {
            TermDictionary t = term("용어" + i, "설명" + i);
            save(t.getId());
        }

        mockMvc.perform(get("/api/mypage/saved-terms")
                        .header("Authorization", bearerToken())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.savedTerms.length()", is(2)))
                .andExpect(jsonPath("$.result.hasNext", is(true)));
    }

    @Test
    @DisplayName("size만큼 가져온 후 다음 페이지를 요청하면 나머지가 반환되고 hasNext=false가 된다")
    void getSavedTerms_pagination_secondPage() throws Exception {
        for (int i = 0; i < 3; i++) {
            TermDictionary t = term("용어" + i, "설명" + i);
            save(t.getId());
        }

        mockMvc.perform(get("/api/mypage/saved-terms")
                        .header("Authorization", bearerToken())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.savedTerms.length()", is(1)))
                .andExpect(jsonPath("$.result.page", is(1)))
                .andExpect(jsonPath("$.result.hasNext", is(false)));
    }

    @Test
    @DisplayName("다른 유저가 저장한 용어는 내 목록에 섞이지 않는다")
    void getSavedTerms_excludesOtherUsersTerms() throws Exception {
        TermDictionary mine = term("내가저장한용어", "설명1");
        save(mine.getId());
        TermDictionary others = term("남이저장한용어", "설명2");
        save(OTHER_USER_ID, others.getId());

        mockMvc.perform(get("/api/mypage/saved-terms").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.savedTerms.length()", is(1)))
                .andExpect(jsonPath("$.result.savedTerms[0].term", is("내가저장한용어")));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void getSavedTerms_unauthorized() throws Exception {
        mockMvc.perform(get("/api/mypage/saved-terms")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("size가 50을 초과하면 400")
    void getSavedTerms_sizeOver50_badRequest() throws Exception {
        mockMvc.perform(get("/api/mypage/saved-terms")
                        .header("Authorization", bearerToken())
                        .param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sort 값이 올바르지 않으면 400")
    void getSavedTerms_invalidSort_badRequest() throws Exception {
        mockMvc.perform(get("/api/mypage/saved-terms")
                        .header("Authorization", bearerToken())
                        .param("sort", "popular"))
                .andExpect(status().isBadRequest());
    }
}
