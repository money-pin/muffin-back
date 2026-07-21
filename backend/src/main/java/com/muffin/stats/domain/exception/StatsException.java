package com.muffin.stats.domain.exception;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;

public class StatsException extends GeneralException {

    public StatsException(StatsErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }
}
