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

    /**
     * 뉴스의 해설 카드를 조회한다.
     *
     * <p>생성에 성공하면 카드가 반드시 1개 이상 저장되므로, 완료된 카드가 하나도 없다는 것은 아직 생성 중이거나 생성에 실패한 상태를 뜻한다.
     * 이 경우 빈 목록 대신 {@link NewsErrorCode#NEWS_EXPLANATION_NOT_COMPLETED}를 던져 정상 조회와 구분한다.
     */
    @Transactional(readOnly = true)
    public NewsExplanationCardsResponse getExplanationCards(Long newsId) {
        if (!newsRepository.existsById(newsId)) {
            throw new NewsException(NewsErrorCode.NEWS_NOT_FOUND);
        }

        List<NewsExplanation> explanations = newsExplanationRepository.findTop3ByNewsIdAndStatusOrderByCardOrderAsc(
                newsId, NewsExplanationStatus.DONE);
        if (explanations.isEmpty()) {
            throw new NewsException(NewsErrorCode.NEWS_EXPLANATION_NOT_COMPLETED);
        }

        List<NewsExplanationCardResponse> cards =
                explanations.stream().map(this::toCardResponse).toList();

        return new NewsExplanationCardsResponse(newsId, cards);
    }

    private NewsExplanationCardResponse toCardResponse(NewsExplanation explanation) {
        return new NewsExplanationCardResponse(
                explanation.getCardOrder(), explanation.getTitle(), explanation.getKeyTerm(), explanation.getContent());
    }
}
