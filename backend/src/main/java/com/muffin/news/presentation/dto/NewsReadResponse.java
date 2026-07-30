package com.muffin.news.presentation.dto;

/** 뉴스 열람 처리 후 갱신된 조회수를 반환한다. */
public record NewsReadResponse(Long viewCount) {}
