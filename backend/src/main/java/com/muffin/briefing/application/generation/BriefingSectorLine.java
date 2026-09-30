package com.muffin.briefing.application.generation;

import java.math.BigDecimal;

/**
 * 계산이 끝난 섹터 등락률 한 줄. AI에는 참고 맥락으로만 넘기며, AI가 이 숫자를 바꾸거나 새로 만들어서는 안 된다.
 *
 * @param changeRate 소수점 둘째 자리까지의 등락률(%)
 */
public record BriefingSectorLine(String sectorName, BigDecimal changeRate) {}
