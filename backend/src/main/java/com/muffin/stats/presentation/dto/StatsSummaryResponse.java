package com.muffin.stats.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.muffin.stats.domain.InvestmentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 수익 통계 조회 응답. 누적 손익 + 최근 7일 누적 수익률 그래프 + 수익 TOP3 섹터 + 투자 성향 카드를 한 번에 내려준다.
 *
 * <p>정산 완료 이력이 없는 신규 사용자는 investDate/investmentType이 null이고 graph/topSectors가 빈 배열이다. 하위 응답 요소는 이 응답에서만 쓰이므로 함께
 * 중첩해 둔다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StatsSummaryResponse(
        LocalDate investDate,
        long cumulativeProfitAmount,
        BigDecimal cumulativeProfitRate,
        List<GraphPointResponse> graph,
        List<TopSectorResponse> topSectors,
        InvestmentTypeResponse investmentType) {

    /** 최근 7일 누적 수익률 그래프의 한 점. 투자하지 않은 날은 직전 누적 수익률을 그대로 유지한다(손실 시 음수). */
    public record GraphPointResponse(LocalDate date, BigDecimal cumulativeProfitRate) {}

    /** 누적 수익률 상위 섹터. profitRate는 그 섹터의 누적 매수금 대비 손익률(%)이다. */
    public record TopSectorResponse(
            int rank, String sectorCode, String sectorName, long profitAmount, BigDecimal profitRate) {}

    /** 투자 성향 카드. type은 STABLE/BALANCED/GROWTH/AGGRESSIVE, bullets는 자산군 비중(불릿1) + 고정 문구 2개다. */
    public record InvestmentTypeResponse(String type, String label, String description, List<String> bullets) {

        public static InvestmentTypeResponse of(InvestmentType type, String assetRatioBullet) {
            return new InvestmentTypeResponse(
                    type.name(), type.label(), type.description(), type.bullets(assetRatioBullet));
        }
    }
}
