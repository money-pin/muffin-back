package com.muffin.quiz.presentation.dto;

import java.util.List;

public record QuizQuestionResponse(Long quizId, int quizOrder, String question, List<QuizOptionResponse> options) {}
