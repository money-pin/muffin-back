package com.muffin.global.apiPayload.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import java.util.Objects;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public GeneralException(BaseErrorCode errorCode) {
        super(Objects.requireNonNull(errorCode, "errorCode must not be null").getMessage());
        this.errorCode = errorCode;
    }

    public GeneralException(BaseErrorCode errorCode, String detailMessage) {
        super(
                (detailMessage == null || detailMessage.isBlank())
                        ? Objects.requireNonNull(errorCode, "errorCode must not be null")
                                .getMessage()
                        : detailMessage);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
    }
}
