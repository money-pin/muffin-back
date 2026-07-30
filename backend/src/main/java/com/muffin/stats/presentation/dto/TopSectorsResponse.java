package com.muffin.stats.presentation.dto;

import java.util.List;

/**
 * 수익 TOP3 섹터 조회 응답(STATS-02-2). 홈 화면처럼 통계 전체가 아니라 TOP3만 필요한 화면을 위해 수익 통계 조회(STATS-02-1)에서 이 부분만 떼어낸
 * 엔드포인트의 응답이다.
 *
 * <p>정렬·선정 규칙과 항목 형태는 STATS-02-1의 {@code topSectors}와 완전히 동일하다(같은 조립 로직을 쓴다). 정산 완료 이력이 없거나 매수금이 있는 섹터가
 * 없으면 빈 배열로 내려간다.
 */
public record TopSectorsResponse(List<TopSectorResponse> topSectors) {}
