package com.muffin.news.application.rss;

import com.muffin.news.domain.category.Category;
import com.muffin.news.domain.category.CategoryRepository;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RssFeedWriter {

    private final CategoryRepository categoryRepository;
    private final NewsRepository newsRepository;

    public RssFeedWriter(CategoryRepository categoryRepository, NewsRepository newsRepository) {
        this.categoryRepository = categoryRepository;
        this.newsRepository = newsRepository;
    }

    /** 한 피드의 신규 기사만 별도 트랜잭션으로 저장한다. */
    @Transactional
    public List<Long> save(String categoryName, String publisher, List<RssArticle> articles) {
        Category category = categoryRepository
                .findByName(categoryName)
                .orElseThrow(() -> new IllegalStateException("RSS category not found: " + categoryName));
        List<Long> collectedIds = new ArrayList<>();
        for (RssArticle article : articles) {
            if (newsRepository.existsByOriginalUrl(article.url())) {
                continue;
            }
            News news = News.processing(
                    category.getId(), article.title(), publisher, article.publishedAt(), null, article.url());
            collectedIds.add(newsRepository.save(news).getId());
        }
        return collectedIds;
    }
}
