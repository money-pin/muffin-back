package com.muffin.global.apiPayload.code;

import org.springframework.http.HttpStatus;

public interface BaseSuccessCode extends BaseCode {

    HttpStatus getHttpStatus();
}
