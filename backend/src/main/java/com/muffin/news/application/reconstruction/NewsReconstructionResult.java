package com.muffin.news.application.reconstruction;

import java.util.List;

public record NewsReconstructionResult(String summary, String rewrittenBody, List<String> warningFlags) {

    /** 뉴스 재구성 결과를 생성하고 경고 플래그 목록의 불변성을 보장한다. */
    public NewsReconstructionResult {
        if (warningFlags == null) {
            throw new IllegalArgumentException("warningFlags must not be null");
        }
        warningFlags = List.copyOf(warningFlags);
    }
}
