package com.muffin.stats.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 자산군 비중으로 투자 성향을 판정하는 규칙과 우선순위를 검증한다. */
class InvestmentTypeTest {

    @Test
    @DisplayName("기술주 비중 70% 이상이면 공격투자형")
    void classify_aggressiveByTech() {
        assertEquals(InvestmentType.AGGRESSIVE, InvestmentType.classify(70, 10, 40));
    }

    @Test
    @DisplayName("단일 섹터 비중 50% 이상이면 기술주가 낮아도 공격투자형")
    void classify_aggressiveBySingleSector() {
        assertEquals(InvestmentType.AGGRESSIVE, InvestmentType.classify(20, 55, 55));
    }

    @Test
    @DisplayName("기술주 비중 50~69% 면 성장추구형")
    void classify_growth() {
        assertEquals(InvestmentType.GROWTH, InvestmentType.classify(56, 44, 28));
        assertEquals(InvestmentType.GROWTH, InvestmentType.classify(69, 10, 40));
    }

    @Test
    @DisplayName("성장 조건이 안정 조건보다 우선한다")
    void classify_growthOverStable() {
        assertEquals(InvestmentType.GROWTH, InvestmentType.classify(55, 60, 40));
    }

    @Test
    @DisplayName("기초자산 비중 60% 이상이면 안정추구형")
    void classify_stable() {
        assertEquals(InvestmentType.STABLE, InvestmentType.classify(20, 70, 40));
    }

    @Test
    @DisplayName("어느 조건에도 안 걸리면 균형형")
    void classify_balanced() {
        assertEquals(InvestmentType.BALANCED, InvestmentType.classify(35, 40, 40));
    }

    @Test
    @DisplayName("불릿은 런타임 자산군 비중(불릿1) + 고정 문구 2개로 3개다")
    void bullets_composeThree() {
        List<String> bullets = InvestmentType.BALANCED.bullets("기초 자산 40%, 기술주 35%, 실물 경제 25%");
        assertEquals(3, bullets.size());
        assertEquals("기초 자산 40%, 기술주 35%, 실물 경제 25%", bullets.getFirst());
    }
}
