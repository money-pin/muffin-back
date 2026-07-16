package com.muffin.news.application.publication;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsPublicationService {

    private final NewsRepository newsRepository;

    /** AI 처리가 끝난 발행 대기 뉴스를 모두 사용자 공개 상태로 변경한다. */
    @Transactional
    public int publishPendingNews() {
        List<News> pendingNews = newsRepository.findAllByStatus(NewsStatus.PENDING);
        pendingNews.forEach(News::publish);
        return pendingNews.size();
    }
}
