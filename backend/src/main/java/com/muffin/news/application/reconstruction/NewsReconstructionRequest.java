package com.muffin.news.application.reconstruction;

import java.time.LocalDateTime;

/** 메모리에 기사 원문 보관 */
public record NewsReconstructionRequest(
        String title, String publisher, LocalDateTime publishedAt, String originalContent) {}
