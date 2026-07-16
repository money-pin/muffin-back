package com.muffin.news.application.reconstruction;

public interface NewsArticleContentClient {

    /** 원문 URL에서 기사 본문만 추출한다. */
    String fetch(String originalUrl);
}
