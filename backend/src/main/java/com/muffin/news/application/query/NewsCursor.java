package com.muffin.news.application.query;

import java.time.LocalDateTime;

/** 뉴스 목록 커서 페이지네이션 기준값. {@code publishedAt DESC, newsId DESC} 정렬과 짝을 이룬다. */
public record NewsCursor(LocalDateTime publishedAt, Long newsId) {}
