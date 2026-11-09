package com.muffin.notification.application.message;

import com.muffin.notification.application.dispatch.PushMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 알림 문구. 기획서(NOTI-01~05)의 문구와 이동 화면을 한곳에 모은다.
 *
 * <p>숫자를 어떻게 반올림하고 어떤 부호를 붙일지도 여기서 정한다. 같은 수치가 앱 화면에 보이는 값과 달라지면 사용자는 알림을 믿지 않는다.
 */
public final class NotificationMessages {

    private static final int MAX_NEWS_TITLE_LENGTH = 30;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d");
    /** 손실 금액 앞에 붙이는 기호. 하이픈(-)이 아니라 기획서의 마이너스 기호(U+2212)다. */
    private static final String MINUS = "−";

    private NotificationMessages() {}

    /** NOTI-01. 본문은 브리핑의 오늘의 한 줄(40자 이내)을 그대로 쓴다. */
    public static PushMessage briefing(LocalDate briefingDate, String headline) {
        String body = (headline == null || headline.isBlank()) ? "오늘 아침 경제 흐름을 1분 만에 훑어봐요." : headline.strip();
        return new PushMessage("모닝 머핀 브리핑이 구워졌어요 🧁", body, "muffin://briefing/" + briefingDate);
    }

    /** NOTI-02. */
    public static PushMessage quizReminder() {
        return new PushMessage("오늘의 한입 퀴즈 도착! 🧁", "3문항 풀고 최대 30만원 받아가세요.", "muffin://quiz/today");
    }

    /**
     * NOTI-03. 수익률은 당일 손익 ÷ 당일 투자금으로 소수 첫째 자리에서 반올림한다. 통계 화면의 기간 매매수익률(DAY)과 같은 계산이다.
     *
     * @param investDate 투자일. 주말·공휴일 뒤 발송분도 이 날짜로 표기한다
     */
    public static PushMessage investmentResult(LocalDate investDate, long profitLoss, long investedAmount) {
        String date = investDate.format(MONTH_DAY);
        String deepLink = "muffin://home?popup=settlement";
        if (profitLoss == 0) {
            return new PushMessage(date + " 투자 결과가 나왔어요", "이번엔 본전이에요. 오늘의 투자를 시작해 보세요!", deepLink);
        }
        String rate = dailyRate(profitLoss, investedAmount).abs().toPlainString();
        String amount = formatAmount(Math.abs(profitLoss));
        if (profitLoss > 0) {
            return new PushMessage(
                    "머핀이 노릇노릇 구워졌어요", date + " 투자 결과 +" + amount + "원 (▲" + rate + "%). 확인해 보세요!", deepLink);
        }
        return new PushMessage(
                "머핀이 조금 탔어요", date + " 투자 결과 " + MINUS + amount + "원 (▼" + rate + "%). 오늘 다시 구워봐요!", deepLink);
    }

    /**
     * NOTI-04. 다섯 문구 중 위에서부터 처음 해당하는 하나를 고른다.
     *
     * @param previousPosition 전전주 순위. 전전주 기록이 없으면 {@code null}
     * @param firstRanking 이번이 이 사용자의 첫 주간 랭킹 기록인지
     */
    public static PushMessage ranking(
            int position, int percentile, BigDecimal weeklyRate, Integer previousPosition, boolean firstRanking) {
        String deepLink = "muffin://ranking";
        BigDecimal rate = weeklyRate == null ? BigDecimal.ZERO : weeklyRate;
        String signedRate = (rate.signum() < 0 ? "▼" : "▲")
                + rate.abs().setScale(2, RoundingMode.HALF_UP).toPlainString();

        if (position <= 3) {
            return new PushMessage(
                    "🏆 지난주 " + position + "위! 시상대에 올랐어요",
                    "수익률 " + signedRate + "%로 상위 " + percentile + "%예요.",
                    deepLink);
        }
        if (position <= 10 && (previousPosition == null || previousPosition > 10)) {
            return new PushMessage("TOP 10에 들었어요! 🎉", "지난주 " + position + "위, 수익률 " + signedRate + "%", deepLink);
        }
        if (firstRanking) {
            return new PushMessage(
                    "첫 주간 랭킹이 나왔어요 🧁", "지난주 " + position + "위 (상위 " + percentile + "%). 확인해 보세요!", deepLink);
        }
        if (previousPosition != null && previousPosition > position) {
            return new PushMessage(
                    "지난주 " + position + "위, " + (previousPosition - position) + "계단 올랐어요 ▲",
                    "이번 주에도 머핀을 잘 구워봐요!",
                    deepLink);
        }
        return new PushMessage("지난주 랭킹이 나왔어요 📊", position + "위 (상위 " + percentile + "%). 이번 주에 다시 올라가 봐요!", deepLink);
    }

    /**
     * NOTI-05.
     *
     * @param publishedCount 이번에 새로 공개된 뉴스 수. 대표 뉴스를 포함한다
     */
    public static PushMessage news(Long newsId, String headlineTitle, int publishedCount) {
        String title = truncate(headlineTitle.strip(), MAX_NEWS_TITLE_LENGTH);
        String body = publishedCount > 1 ? title + " 외 " + (publishedCount - 1) + "건" : title;
        return new PushMessage("오늘의 뉴스가 구워졌어요", body, "muffin://news/" + newsId);
    }

    private static BigDecimal dailyRate(long profitLoss, long investedAmount) {
        if (investedAmount <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(profitLoss)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(investedAmount), 1, RoundingMode.HALF_UP);
    }

    private static String formatAmount(long amount) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(amount);
    }

    private static String truncate(String value, int maxLength) {
        if (value.codePointCount(0, value.length()) <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength)) + "…";
    }
}
