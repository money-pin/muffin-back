package com.muffin.stats.presentation.dto;

import com.muffin.stats.domain.InvestmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 수익 통계 조회 응답(STATS-02-1). 누적 손익 + 최근 7일 누적 수익률 그래프 + 수익 TOP3 섹터 + 투자 성향 카드를 한 번에 내려준다.
 *
 * <p>정산 완료 이력이 없는 신규 사용자는 investDate/investmentType이 null이고, graph는 오늘 포함 7일치가 전부 0%로, topSectors는 빈 배열로
 * 내려간다. 하위 응답 요소는 이 응답에서만 쓰이므로 함께 중첩해 두되, {@link TopSectorResponse}만은 TOP3 단독 조회(STATS-02-2)와 공유하므로 별도
 * 파일로 분리했다.
 */
public record StatsSummaryResponse(
        LocalDate investDate,
        long cumulativeProfitAmount,
        BigDecimal cumulativeProfitRate,
        List<GraphPointResponse> graph,
        List<TopSectorResponse> topSectors,
        InvestmentTypeResponse investmentType) {

    /** 최근 7일 누적 수익률 그래프의 한 점. 투자하지 않은 날은 직전 누적 수익률을 그대로 유지한다(손실 시 음수). */
    public record GraphPointResponse(LocalDate date, BigDecimal cumulativeProfitRate) {}

    /** 투자 성향 카드. type은 STABLE/BALANCED/GROWTH/AGGRESSIVE, bullets는 자산군 비중(불릿1) + 고정 문구 2개다. */
    public record InvestmentTypeResponse(String type, String label, String description, List<String> bullets) {

        public static InvestmentTypeResponse of(InvestmentType type, String assetRatioBullet) {
            return new InvestmentTypeResponse(
                    type.name(), type.label(), type.description(), type.bullets(assetRatioBullet));
        }
    }
}
