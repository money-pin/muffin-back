package com.muffin.global.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode extends BaseCode {

    HttpStatus getHttpStatus();
}
