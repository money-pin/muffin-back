package com.muffin.quiz.application.generation;

/** 일일 퀴즈 생성 결과를 저장할 수 없는 구체적인 실패 사유. */
public enum DailyQuizGenerationFailureReason {
    INVALID_QUESTION_COUNT,
    DUPLICATED_QUESTION_ORDER,
    QUESTION_NEWS_MISMATCH,
    INVALID_QUESTION_ORDER,
    INVALID_OPTION_COUNT,
    INVALID_OPTION_ORDER,
    INVALID_CORRECT_OPTION_ORDER,
    NUMERIC_RECALL_QUESTION,
    CORRECT_OPTION_NOT_FOUND,
    SOURCE_SENTENCE_NOT_FOUND
}
