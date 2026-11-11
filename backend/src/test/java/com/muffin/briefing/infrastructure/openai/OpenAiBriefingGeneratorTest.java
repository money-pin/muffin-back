package com.muffin.briefing.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.muffin.briefing.application.exception.BriefingGenerationException;
import com.muffin.briefing.application.generation.BriefingGenerationRequest;
import com.muffin.briefing.application.generation.BriefingGenerationResult;
import com.muffin.briefing.application.generation.BriefingIssueCandidate;
import com.muffin.briefing.application.generation.BriefingSectorLine;
import com.muffin.briefing.application.generation.BuzzSignal;
import com.muffin.news.infrastructure.openai.OpenAiClientProperties;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** 섹터 변동 이유를 주고받는 부분을 검증한다. 모델 응답이 성적표와 어긋나면 그대로 저장되면 안 된다. */
class OpenAiBriefingGeneratorTest {

    private static final LocalDate BRIEFING_DATE = LocalDate.of(2026, 7, 20);

    private static final String ISSUES =
            """
            {"news_id":101,"title":"금리 동결","summary":"한국은행이 기준금리를 묶었어요. 물가를 지켜보기로 했어요.","impact_line":"대출 이자 부담은 그대로예요."},
            {"news_id":102,"title":"환율 상승","summary":"원/달러 환율이 올랐어요. 달러 강세가 이어졌어요.","impact_line":"해외 결제 비용이 늘 수 있어요."},
            {"news_id":103,"title":"반도체 수출 증가","summary":"반도체 수출이 늘었어요. AI 서버 주문이 이어졌어요.","impact_line":"반도체 흐름 참고에 좋아요."}
            """;

    @Test
    void parsesSectorReasonsForEachSector() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiBriefingGenerator generator = generator(builder);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"sector":"반도체","reason":"엔비디아 AI 칩 수요가 늘며 HBM 공급이 빠듯해졌어요. 국내 반도체는 이 밸류체인에 엮여 있어 함께 올랐어요."},
                                {"sector":"2차전지","reason":"전기차 수요 둔화 우려가 이어졌어요."}
                                """),
                        MediaType.APPLICATION_JSON));

        BriefingGenerationResult result = generator.generate(request());

        assertThat(result.sectorReasons())
                .extracting(BriefingGenerationResult.SectorReasonResult::sectorName)
                .containsExactly("반도체", "2차전지");
        assertThat(result.sectorReasons().getFirst().reason()).contains("HBM");
        server.verify();
    }

    /** 성적표에 없는 섹터를 설명하면 지어낸 것이다. 사용자는 이 문장을 사실로 읽으므로 저장 전에 막는다. */
    @Test
    void rejectsReasonForSectorOutsideScoreboard() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiBriefingGenerator generator = generator(builder);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"sector":"반도체","reason":"AI 수요가 이어졌어요."},
                                {"sector":"조선","reason":"성적표에 없는 섹터예요."}
                                """),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> generator.generate(request()))
                .isInstanceOf(BriefingGenerationException.class)
                .hasMessageContaining("조선");
    }

    /** 한 섹터를 두 번 설명했다면 다른 섹터가 빠졌다는 뜻이라 그대로 두면 안 된다. */
    @Test
    void rejectsDuplicatedSectorReason() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiBriefingGenerator generator = generator(builder);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"sector":"반도체","reason":"AI 수요가 이어졌어요."},
                                {"sector":"반도체","reason":"같은 섹터를 또 설명했어요."}
                                """),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> generator.generate(request()))
                .isInstanceOf(BriefingGenerationException.class)
                .hasMessageContaining("두 번");
    }

    /** 일부 섹터 설명이 빠져도 브리핑은 살린다. 설명 없는 섹터는 등락률만 보여주면 된다. */
    @Test
    void acceptsPartiallyMissingSectorReasons() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiBriefingGenerator generator = generator(builder);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        response(
                                """
                                {"sector":"반도체","reason":"AI 수요가 이어졌어요."}
                                """),
                        MediaType.APPLICATION_JSON));

        BriefingGenerationResult result = generator.generate(request());

        assertThat(result.sectorReasons()).hasSize(1);
    }

    /** 성적표가 비면 설명할 대상도 없다. 스키마에서 빠지므로 응답에도 없어야 한다. */
    @Test
    void returnsNoSectorReasonsWhenScoreboardIsEmpty() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenAiBriefingGenerator generator = generator(builder);
        server.expect(requestTo("https://api.test/responses"))
                .andRespond(withSuccess(
                        envelope("{\"headline\":\"오늘은 금리에 시선이 쏠렸어요.\",\"issues\":[" + ISSUES.replace("\n", "") + "]}"),
                        MediaType.APPLICATION_JSON));

        BriefingGenerationResult result = generator.generate(
                new BriefingGenerationRequest(BRIEFING_DATE, candidates(), BuzzSignal.empty(), List.of(), List.of()));

        assertThat(result.sectorReasons()).isEmpty();
        server.verify();
    }

    private static OpenAiBriefingGenerator generator(RestClient.Builder builder) {
        return new OpenAiBriefingGenerator(
                builder.build(),
                new ObjectMapper(),
                new OpenAiClientProperties("test-key", "https://api.test/responses"),
                new BriefingGenerationProperties("gpt-6-luna"));
    }

    private static BriefingGenerationRequest request() {
        return new BriefingGenerationRequest(
                BRIEFING_DATE,
                candidates(),
                BuzzSignal.empty(),
                List.of(
                        new BriefingSectorLine("반도체", new BigDecimal("2.31")),
                        new BriefingSectorLine("2차전지", new BigDecimal("-1.04"))),
                List.of());
    }

    private static List<BriefingIssueCandidate> candidates() {
        return List.of(
                new BriefingIssueCandidate(101L, "금리 동결", "기준금리 동결", "경제", List.of()),
                new BriefingIssueCandidate(102L, "환율 상승", "원/달러 상승", "경제", List.of()),
                new BriefingIssueCandidate(103L, "반도체 수출 증가", "수출 회복", "산업", List.of()));
    }

    /** sector_reasons 배열 원소를 받아 브리핑 응답 본문으로 감싼다. */
    private static String response(String sectorReasons) {
        return envelope("{\"headline\":\"오늘은 금리에 시선이 쏠렸어요.\",\"issues\":[" + ISSUES.replace("\n", "")
                + "],\"sector_reasons\":[" + sectorReasons.replace("\n", "") + "]}");
    }

    /** 브리핑 JSON을 Responses API 응답 봉투로 감싼다. */
    private static String envelope(String payload) {
        return "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":"
                + new ObjectMapper().writeValueAsString(payload) + "}]}]}";
    }
}
