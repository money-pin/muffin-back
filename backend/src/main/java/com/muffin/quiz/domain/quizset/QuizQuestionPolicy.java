package com.muffin.quiz.domain.quizset;

import java.util.Set;

public final class QuizQuestionPolicy {

    public static final Set<String> NUMERIC_RECALL_QUESTION_PHRASES =
            Set.of("몇 년", "몇 개월", "몇 %", "몇 퍼센트", "몇 조", "몇 원", "얼마입니까", "얼마인가요");

    private QuizQuestionPolicy() {}
}
