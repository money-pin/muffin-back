package com.muffin.news.application.reconstruction;

public interface NewsRewriter {

    /** 기사 원문을 금융 입문자용 한 줄 요약과 본문으로 재구성한다. */
    NewsReconstructionResult rewrite(NewsReconstructionRequest request);
}
