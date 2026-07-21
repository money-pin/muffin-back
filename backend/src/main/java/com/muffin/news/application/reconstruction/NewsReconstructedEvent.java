package com.muffin.news.application.reconstruction;

/** 뉴스 재구성 결과가 저장된 뒤 후속 파이프라인에 전달하는 이벤트. */
public record NewsReconstructedEvent(Long newsId) {}
