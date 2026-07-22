package com.muffin.news.application.query;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 뉴스 썸네일 대체 정책 설정.
 *
 * @param todayDefaultKey 크게 노출되는 화면(오늘의 뉴스 히어로 카드, 상세 페이지) 전용 기본 이미지의 S3 오브젝트
 *     키. 목록용 카테고리별 기본 이미지와 노출 크기가 달라 별도 에셋을 쓴다.
 */
@ConfigurationProperties(prefix = "muffin.news.thumbnail")
public record NewsThumbnailProperties(String todayDefaultKey) {}
