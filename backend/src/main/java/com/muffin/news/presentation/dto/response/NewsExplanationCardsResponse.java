package com.muffin.news.presentation.dto.response;

import java.util.List;

public record NewsExplanationCardsResponse(Long newsId, List<NewsExplanationCardResponse> cards) {

    public NewsExplanationCardsResponse {
        cards = List.copyOf(cards);
    }
}
