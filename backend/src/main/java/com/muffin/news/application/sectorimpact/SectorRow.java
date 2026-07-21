package com.muffin.news.application.sectorimpact;

/** 섹터 영향도 응답 구성을 위한 섹터 마스터 읽기 전용 프로젝션(표시 순서대로 조회). */
public record SectorRow(Long sectorId, String sectorCode, String sectorName) {}
