package com.muffin.news.application.query;

import java.time.LocalDateTime;

/** 마이페이지 홈에 표시할 최근 열람 뉴스 조회용 읽기 전용 프로젝션. */
public record RecentReadNewsRow(Long newsId, String title, String thumbnailUrl, LocalDateTime readAt) {}
