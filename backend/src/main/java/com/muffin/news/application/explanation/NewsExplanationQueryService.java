package com.muffin.news.application.explanation;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.presentation.dto.response.NewsExplanationCardResponse;
import com.muffin.news.presentation.dto.response.NewsExplanationCardsResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsExplanationQueryService {

    private final NewsRepository newsRepository;
    private final NewsExplanationRepository newsExplanationRepository;

    @Transactional(readOnly = true)
    public NewsExplanationCardsResponse getExplanationCards(Long newsId) {
        if (!newsRepository.existsById(newsId)) {
            throw new NewsException(NewsErrorCode.NEWS_NOT_FOUND);
        }

        List<NewsExplanationCardResponse> cards =
                newsExplanationRepository
                        .findTop3ByNewsIdAndStatusOrderByCardOrderAsc(newsId, NewsExplanationStatus.DONE)
                        .stream()
                        .map(this::toCardResponse)
                        .toList();

        return new NewsExplanationCardsResponse(newsId, cards);
    }

    private NewsExplanationCardResponse toCardResponse(NewsExplanation explanation) {
        return new NewsExplanationCardResponse(
                explanation.getCardOrder(), explanation.getTitle(), explanation.getKeyTerm(), explanation.getContent());
    }
}
