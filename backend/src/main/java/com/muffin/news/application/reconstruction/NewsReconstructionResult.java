package com.muffin.news.application.reconstruction;

import java.util.List;

public record NewsReconstructionResult(String summary, String rewrittenBody, List<String> warningFlags) {

    public NewsReconstructionResult {
        if (warningFlags == null) {
            throw new IllegalArgumentException("warningFlags must not be null");
        }
        warningFlags = List.copyOf(warningFlags);
    }
}
