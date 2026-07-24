package com.muffin.quiz.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 일일 퀴즈 AI 생성 설정. */
@ConfigurationProperties(prefix = "muffin.news.ai.quiz")
public record DailyQuizGenerationProperties(String model) {}
