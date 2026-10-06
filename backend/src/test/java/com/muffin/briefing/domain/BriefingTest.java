package com.muffin.briefing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.briefing.domain.enums.SectorRankType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BriefingTest {

    private static final LocalDate BRIEFING_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 20, 7, 30);

    @Test
    @DisplayName("브리핑을 만들면 생성 중 상태로 시작한다")
    void create_startsAsGenerating() {
        assertThat(Briefing.create(BRIEFING_DATE).getStatus()).isEqualTo(BriefingStatus.GENERATING);
    }

    @Test
    @DisplayName("이슈 3건이 모이면 발행 대기 상태가 된다")
    void ready_setsReadyStatusWithHeadlineAndTerm() {
        Briefing briefing = briefingWithIssues();

        briefing.ready("미국 기술주가 쉬어가면서 코스피도 숨 고르기에 들어갔어요.", 7L, "우리나라가 파는 물건의 가격 수준");

        assertThat(briefing.getStatus()).isEqualTo(BriefingStatus.READY);
        assertThat(briefing.getHeadline()).isEqualTo("미국 기술주가 쉬어가면서 코스피도 숨 고르기에 들어갔어요.");
        assertThat(briefing.getTermId()).isEqualTo(7L);
        assertThat(briefing.getTermSummary()).isEqualTo("우리나라가 파는 물건의 가격 수준");
    }

    @Test
    @DisplayName("이슈가 3건이 아니면 발행 대기 상태로 넘어갈 수 없다")
    void ready_throwsWhenIssueCountIsNotThree() {
        Briefing briefing = Briefing.create(BRIEFING_DATE);
        briefing.addIssue(1, 100L, "반도체 수출 증가", "지난달 반도체 수출이 늘었어요. AI 서버용 주문이 이어졌어요.", "반도체 흐름을 볼 때 참고할 만해요.");

        assertThatThrownBy(() -> briefing.ready("한 줄 요약이에요.", null, null)).isInstanceOf(IllegalStateException.class);
    }

    /** 용어는 후보가 없을 수 있다. 둘 중 하나만 들어오면 화면이 반쪽이 되므로 아예 비워 둔다. */
    @Test
    @DisplayName("용어 후보가 없으면 용어 없이 발행 대기 상태가 된다")
    void ready_leavesTermEmptyWhenNotSelected() {
        Briefing briefing = briefingWithIssues();

        briefing.ready("한 줄 요약이에요.", null, null);

        assertThat(briefing.getStatus()).isEqualTo(BriefingStatus.READY);
        assertThat(briefing.getTermId()).isNull();
        assertThat(briefing.getTermSummary()).isNull();
    }

    @Test
    @DisplayName("용어 뜻이 30자를 넘으면 용어를 채우지 않는다")
    void ready_ignoresTermWhenSummaryTooLong() {
        Briefing briefing = briefingWithIssues();

        briefing.ready("한 줄 요약이에요.", 7L, "가".repeat(31));

        assertThat(briefing.getTermId()).isNull();
        assertThat(briefing.getTermSummary()).isNull();
    }

    @Test
    @DisplayName("한 줄 요약이 40자를 넘으면 거절한다")
    void ready_throwsWhenHeadlineTooLong() {
        Briefing briefing = briefingWithIssues();

        assertThatThrownBy(() -> briefing.ready("가".repeat(41), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 뉴스를 두 번 이슈로 넣을 수 없다")
    void addIssue_throwsWhenNewsIsReused() {
        Briefing briefing = Briefing.create(BRIEFING_DATE);
        briefing.addIssue(1, 100L, "제목", "첫 문장이에요. 둘째 문장이에요.", "영향 한 줄이에요.");

        assertThatThrownBy(() -> briefing.addIssue(2, 100L, "다른 제목", "첫 문장이에요. 둘째 문장이에요.", "영향 한 줄이에요."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이슈 제목이 20자를 넘으면 거절한다")
    void addIssue_throwsWhenTitleTooLong() {
        Briefing briefing = Briefing.create(BRIEFING_DATE);

        assertThatThrownBy(() -> briefing.addIssue(1, 100L, "가".repeat(21), "첫 문장이에요. 둘째 문장이에요.", "영향 한 줄이에요."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("섹터 성적표가 비어 있어도 발행할 수 있다")
    void publish_succeedsWithoutSectorScores() {
        Briefing briefing = briefingWithIssues();
        briefing.ready("한 줄 요약이에요.", null, null);

        briefing.publish(PUBLISHED_AT);

        assertThat(briefing.getStatus()).isEqualTo(BriefingStatus.PUBLISHED);
        assertThat(briefing.getPublishedAt()).isEqualTo(PUBLISHED_AT);
        assertThat(briefing.getSectorScores()).isEmpty();
    }

    @Test
    @DisplayName("발행 대기 상태가 아니면 공개할 수 없다")
    void publish_throwsWhenNotReady() {
        Briefing briefing = briefingWithIssues();

        assertThatThrownBy(() -> briefing.publish(PUBLISHED_AT)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 공개된 브리핑은 이용 불가로 되돌릴 수 없다")
    void unavailable_throwsWhenAlreadyPublished() {
        Briefing briefing = briefingWithIssues();
        briefing.ready("한 줄 요약이에요.", null, null);
        briefing.publish(PUBLISHED_AT);

        assertThatThrownBy(briefing::unavailable).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("섹터 성적표는 상승과 하락을 순서대로 담는다")
    void addSectorScore_keepsRankTypeAndOrder() {
        Briefing briefing = Briefing.create(BRIEFING_DATE);

        briefing.addSectorScore(1L, new BigDecimal("1.20"), SectorRankType.TOP_GAINER, 1);
        briefing.addSectorScore(2L, new BigDecimal("-2.30"), SectorRankType.TOP_LOSER, 2);

        assertThat(briefing.getSectorScores())
                .extracting(BriefingSectorScore::getSectorId, BriefingSectorScore::getRankType)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, SectorRankType.TOP_GAINER),
                        org.assertj.core.groups.Tuple.tuple(2L, SectorRankType.TOP_LOSER));
    }

    @Test
    @DisplayName("이슈 목록은 읽기 전용이다")
    void getIssues_isUnmodifiable() {
        Briefing briefing = briefingWithIssues();

        assertThatThrownBy(() -> briefing.getIssues().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }

    private static Briefing briefingWithIssues() {
        Briefing briefing = Briefing.create(BRIEFING_DATE);
        briefing.addIssue(1, 100L, "반도체 수출 증가", "지난달 반도체 수출이 늘었어요. AI 서버용 주문이 이어졌어요.", "반도체 흐름을 볼 때 참고할 만해요.");
        briefing.addIssue(2, 200L, "환율 상승", "원/달러 환율이 올랐어요. 달러 강세가 이어졌어요.", "해외 결제 비용이 늘 수 있어요.");
        briefing.addIssue(3, 300L, "금리 동결", "한국은행이 기준금리를 묶었어요. 물가 흐름을 지켜보기로 했어요.", "대출 이자 부담은 당분간 그대로예요.");
        return briefing;
    }
}
