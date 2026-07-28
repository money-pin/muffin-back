package com.muffin.news.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.sector.application.seed.SectorSeedData;
import com.muffin.sector.application.seed.SectorSeedData.EtfSeed;
import com.muffin.sector.application.seed.SectorSeedData.GroupSeed;
import com.muffin.sector.application.seed.SectorSeedData.SectorSeed;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 뉴스 조회 4종 API를 실제 컨텍스트 + H2로 태워 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NewsQueryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private SectorGroupRepository sectorGroupRepository;

    @Autowired
    private EtfRepository etfRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    private static final Long USER_ID = 1L;

    private Long categoryId;
    private Long news1Id; // 가장 오래된 발행
    private Long news3Id; // 가장 최신 발행
    private Long processingNewsId;

    @BeforeEach
    void setUp() {
        Category category = categoryRepository.save(Category.create("경제", "https://img/economy.png"));
        categoryId = category.getId();

        news1Id = savePublished(category.getId(), "뉴스1", LocalDateTime.of(2026, 7, 16, 9, 0));
        Long news2Id = savePublished(category.getId(), "뉴스2", LocalDateTime.of(2026, 7, 17, 9, 0));
        news3Id = savePublished(category.getId(), "뉴스3", LocalDateTime.of(2026, 7, 18, 9, 0));

        News processing = News.processing(
                category.getId(),
                "처리중뉴스",
                "매일경제",
                LocalDateTime.of(2026, 7, 18, 10, 0),
                null,
                "https://news/processing");
        processingNewsId = newsRepository.save(processing).getId();

        seedSectorMaster();
    }

    /** 섹터 시더는 test 프로파일에서 실행되지 않으므로, 섹터 영향도 조회 검증을 위해 시드 데이터를 직접 적재한다. */
    private void seedSectorMaster() {
        Map<String, Long> groupIds = new HashMap<>();
        for (GroupSeed group : SectorSeedData.GROUPS) {
            groupIds.put(
                    group.groupCode(),
                    sectorGroupRepository
                            .save(SectorGroup.create(
                                    group.groupCode(), group.name(), group.description(), group.groupOrder()))
                            .getId());
        }
        Map<String, Long> etfIds = new HashMap<>();
        for (EtfSeed etf : SectorSeedData.ETFS) {
            etfIds.put(
                    etf.etfCode(),
                    etfRepository.save(Etf.create(etf.etfCode(), etf.etfName())).getId());
        }
        for (SectorSeed sector : SectorSeedData.SECTORS) {
            sectorRepository.save(Sector.create(
                    groupIds.get(sector.groupCode()),
                    etfIds.get(sector.etfCode()),
                    sector.name(),
                    sector.description(),
                    sector.sectorCode(),
                    sector.sectorOrder()));
        }
    }

    private Long savePublished(Long categoryId, String title, LocalDateTime publishedAt) {
        News news = News.processing(categoryId, title, "매일경제", publishedAt, null, "https://news/" + title);
        news.completeReconstruction(title + " 요약", title + " 재구성 본문");
        news.publish();
        return newsRepository.save(news).getId();
    }

    private String bearerToken() {
        return "Bearer " + accessTokenProvider.issue(USER_ID, "USER");
    }

    @Test
    @DisplayName("목록은 최신 발행순으로 커서 페이지네이션되고 PROCESSING은 제외된다")
    void getNews_cursorPagination() throws Exception {
        // 1페이지: 최신 2건(뉴스3, 뉴스2), hasNext=true
        String nextCursor = mockMvc.perform(get("/api/news").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.items.length()").value(2))
                .andExpect(jsonPath("$.result.items[0].title").value("뉴스3"))
                .andExpect(jsonPath("$.result.items[1].title").value("뉴스2"))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.nextCursor").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll(".*\"nextCursor\":\"", "")
                .replaceAll("\".*", "");

        // 2페이지: 마지막 1건(뉴스1), hasNext=false
        mockMvc.perform(get("/api/news").param("size", "2").param("cursor", nextCursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].title").value("뉴스1"))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("size가 허용 범위를 벗어나면 400")
    void getNews_invalidSize() throws Exception {
        mockMvc.perform(get("/api/news").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("커서가 올바르지 않으면 400")
    void getNews_invalidCursor() throws Exception {
        mockMvc.perform(get("/api/news").param("cursor", "!!!not-a-cursor!!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_001"));
    }

    @Test
    @DisplayName("categoryId 필터가 적용된다")
    void getNews_categoryFilter() throws Exception {
        mockMvc.perform(get("/api/news").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(3));

        mockMvc.perform(get("/api/news").param("categoryId", String.valueOf(categoryId + 999)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(0));
    }

    @Test
    @DisplayName("오늘의 뉴스는 당일 공개 뉴스를 최신순 최대 3건 반환한다")
    void getTodayNews() throws Exception {
        mockMvc.perform(get("/api/news/today").header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(3))
                .andExpect(jsonPath("$.result.items[0].title").value("뉴스3"));
    }

    @Test
    @DisplayName("상세 조회 시 조회수가 증가하고 TEXT 세그먼트를 반환한다")
    void getNewsDetail() throws Exception {
        mockMvc.perform(post("/api/news/{id}", news3Id).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.newsId").value(news3Id))
                .andExpect(jsonPath("$.result.viewCount").value(1))
                .andExpect(jsonPath("$.result.categoryName").value("경제"))
                .andExpect(jsonPath("$.result.bodySegments[0].type").value("TEXT"))
                .andExpect(jsonPath("$.result.bodySegments[0].text").value("뉴스3 재구성 본문"))
                .andExpect(jsonPath("$.result.bodySegments[0].termId").doesNotExist())
                .andExpect(jsonPath("$.result.isScrapped").value(false));
    }

    @Test
    @DisplayName("공개되지 않은 뉴스 상세는 403")
    void getNewsDetail_notPublished() throws Exception {
        mockMvc.perform(post("/api/news/{id}", processingNewsId).header("Authorization", bearerToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONTENT_403_001"));
    }

    @Test
    @DisplayName("존재하지 않는 뉴스 상세는 404")
    void getNewsDetail_notFound() throws Exception {
        mockMvc.perform(post("/api/news/{id}", 9_999_999L).header("Authorization", bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONTENT_404_001"));
    }

    @Test
    @DisplayName("섹터 영향도는 마스터의 12개 섹터를 표시 순서로 반환하며 분석 결과가 없으면 NEUTRAL이다")
    void getSectorImpacts_defaultNeutral() throws Exception {
        mockMvc.perform(get("/api/news/{id}/sector-impacts", news1Id).header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.newsId").value(news1Id))
                .andExpect(jsonPath("$.result.sectorImpacts.length()").value(12))
                .andExpect(jsonPath("$.result.sectorImpacts[0].sectorCode").value("DEPOSIT"))
                .andExpect(jsonPath("$.result.sectorImpacts[3].sectorCode").value("USD"))
                .andExpect(jsonPath("$.result.sectorImpacts[7].sectorCode").value("CRYPTO"))
                .andExpect(jsonPath("$.result.sectorImpacts[7].sectorName").value("코인"))
                .andExpect(jsonPath("$.result.sectorImpacts[11].sectorCode").value("DEFENSE"))
                .andExpect(jsonPath("$.result.sectorImpacts[5].impact").value("NEUTRAL"));
    }

    @Test
    @DisplayName("목록 조회는 인증 없이도 200을 반환한다")
    void getNews_allowsAnonymous() throws Exception {
        mockMvc.perform(get("/api/news")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("인증이 필요한 뉴스 API는 토큰이 없으면 401을 반환한다")
    void authenticatedEndpoints_rejectAnonymous() throws Exception {
        mockMvc.perform(get("/api/news/today")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/news/{id}", news3Id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/news/{id}/sector-impacts", news1Id)).andExpect(status().isUnauthorized());
    }
}
