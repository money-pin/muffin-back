package com.muffin.notification.application.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.notification.application.dispatch.PushMessage;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationMessagesTest {

    @Nested
    @DisplayName("NOTI-01 브리핑")
    class Briefing {

        @Test
        @DisplayName("본문은 오늘의 한 줄이고 브리핑 날짜 화면으로 이동한다")
        void usesHeadlineAndDateLink() {
            PushMessage message = NotificationMessages.briefing(LocalDate.of(2026, 10, 20), "미국 기술주가 쉬어가요.");

            assertThat(message.title()).isEqualTo("모닝 머핀 브리핑이 구워졌어요 🧁");
            assertThat(message.body()).isEqualTo("미국 기술주가 쉬어가요.");
            assertThat(message.deepLink()).isEqualTo("muffin://briefing/2026-10-20");
        }

        @Test
        @DisplayName("한 줄이 비어 있으면 기본 문구를 쓴다")
        void fallsBackWhenHeadlineBlank() {
            assertThat(NotificationMessages.briefing(LocalDate.of(2026, 10, 20), " ")
                            .body())
                    .isEqualTo("오늘 아침 경제 흐름을 1분 만에 훑어봐요.");
        }
    }

    @Nested
    @DisplayName("NOTI-03 투자 결과")
    class InvestmentResult {

        @Test
        @DisplayName("수익이면 +금액과 ▲수익률(당일 손익 ÷ 당일 투자금, 소수 첫째 자리)을 쓴다")
        void profit() {
            PushMessage message = NotificationMessages.investmentResult(LocalDate.of(2026, 10, 16), 12_345L, 300_000L);

            assertThat(message.title()).isEqualTo("머핀이 노릇노릇 구워졌어요");
            // 12,345 / 300,000 = 4.115% → 4.1%
            assertThat(message.body()).isEqualTo("10/16 투자 결과 +12,345원 (▲4.1%). 확인해 보세요!");
            assertThat(message.deepLink()).isEqualTo("muffin://home?popup=settlement");
        }

        @Test
        @DisplayName("손실이면 마이너스 기호(U+2212)와 ▼를 쓴다")
        void loss() {
            PushMessage message = NotificationMessages.investmentResult(LocalDate.of(2026, 10, 16), -5_000L, 200_000L);

            assertThat(message.title()).isEqualTo("머핀이 조금 탔어요");
            assertThat(message.body()).isEqualTo("10/16 투자 결과 −5,000원 (▼2.5%). 오늘 다시 구워봐요!");
        }

        @Test
        @DisplayName("손익이 0원이면 보합 문구를 쓴다")
        void breakEven() {
            PushMessage message = NotificationMessages.investmentResult(LocalDate.of(2026, 10, 16), 0L, 200_000L);

            assertThat(message.title()).isEqualTo("10/16 투자 결과가 나왔어요");
            assertThat(message.body()).isEqualTo("이번엔 본전이에요. 오늘의 투자를 시작해 보세요!");
        }
    }

    @Nested
    @DisplayName("NOTI-04 랭킹")
    class Ranking {

        private static final BigDecimal RATE = new BigDecimal("3.5");

        @Test
        @DisplayName("1~3위는 다른 조건보다 먼저 시상대 문구를 쓴다")
        void podium() {
            PushMessage message = NotificationMessages.ranking(2, 5, RATE, null, true);

            assertThat(message.title()).isEqualTo("🏆 지난주 2위! 시상대에 올랐어요");
            assertThat(message.body()).isEqualTo("수익률 ▲3.50%로 상위 5%예요.");
            assertThat(message.deepLink()).isEqualTo("muffin://ranking");
        }

        @Test
        @DisplayName("손실 주간이면 ▼로 표기한다")
        void podiumWithLoss() {
            assertThat(NotificationMessages.ranking(1, 1, new BigDecimal("-1.2"), 1, false)
                            .body())
                    .isEqualTo("수익률 ▼1.20%로 상위 1%예요.");
        }

        @Test
        @DisplayName("전전주 11위 이하에서 TOP 10에 들면 TOP 10 문구를 쓴다")
        void enteredTop10() {
            PushMessage message = NotificationMessages.ranking(7, 20, RATE, 15, false);

            assertThat(message.title()).isEqualTo("TOP 10에 들었어요! 🎉");
            assertThat(message.body()).isEqualTo("지난주 7위, 수익률 ▲3.50%");
        }

        @Test
        @DisplayName("이미 TOP 10이던 사용자는 TOP 10 문구가 아니라 순위 변동 문구를 받는다")
        void stayedInTop10() {
            assertThat(NotificationMessages.ranking(5, 10, RATE, 8, false).title())
                    .isEqualTo("지난주 5위, 3계단 올랐어요 ▲");
        }

        @Test
        @DisplayName("첫 랭킹이면 첫 랭킹 문구를 쓴다")
        void firstRanking() {
            PushMessage message = NotificationMessages.ranking(30, 60, RATE, null, true);

            assertThat(message.title()).isEqualTo("첫 주간 랭킹이 나왔어요 🧁");
            assertThat(message.body()).isEqualTo("지난주 30위 (상위 60%). 확인해 보세요!");
        }

        @Test
        @DisplayName("전전주만 쉰 복귀 사용자는 비교할 순위가 없어 기본 문구를 받는다")
        void returningUser() {
            PushMessage message = NotificationMessages.ranking(30, 60, RATE, null, false);

            assertThat(message.title()).isEqualTo("지난주 랭킹이 나왔어요 📊");
            assertThat(message.body()).isEqualTo("30위 (상위 60%). 이번 주에 다시 올라가 봐요!");
        }

        @Test
        @DisplayName("순위가 그대로거나 내려가면 기본 문구를 받는다")
        void droppedOrSame() {
            assertThat(NotificationMessages.ranking(30, 60, RATE, 30, false).title())
                    .isEqualTo("지난주 랭킹이 나왔어요 📊");
            assertThat(NotificationMessages.ranking(30, 60, RATE, 20, false).title())
                    .isEqualTo("지난주 랭킹이 나왔어요 📊");
        }
    }

    @Nested
    @DisplayName("NOTI-05 뉴스")
    class News {

        @Test
        @DisplayName("대표 뉴스 제목 외 나머지 건수를 붙이고 대표 뉴스 상세로 이동한다")
        void withOthers() {
            PushMessage message = NotificationMessages.news(42L, "반도체 수출, 3개월째 증가", 5);

            assertThat(message.title()).isEqualTo("오늘의 뉴스가 구워졌어요");
            assertThat(message.body()).isEqualTo("반도체 수출, 3개월째 증가 외 4건");
            assertThat(message.deepLink()).isEqualTo("muffin://news/42");
        }

        @Test
        @DisplayName("신규 뉴스가 1건이면 '외 N건'을 생략한다")
        void single() {
            assertThat(NotificationMessages.news(42L, "반도체 수출, 3개월째 증가", 1).body())
                    .isEqualTo("반도체 수출, 3개월째 증가");
        }

        @Test
        @DisplayName("제목이 30자를 넘으면 30자에서 자르고 말줄임표를 붙인다")
        void truncatesLongTitle() {
            String title = "가".repeat(31);

            assertThat(NotificationMessages.news(42L, title, 1).body()).isEqualTo("가".repeat(30) + "…");
        }
    }

    @Test
    @DisplayName("NOTI-02 퀴즈 리마인드는 오늘의 퀴즈 화면으로 이동한다")
    void quizReminder() {
        PushMessage message = NotificationMessages.quizReminder();

        assertThat(message.title()).isEqualTo("오늘의 한입 퀴즈 도착! 🧁");
        assertThat(message.deepLink()).isEqualTo("muffin://quiz/today");
    }
}
