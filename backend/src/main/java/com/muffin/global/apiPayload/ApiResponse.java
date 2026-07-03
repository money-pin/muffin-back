package com.muffin.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.muffin.global.apiPayload.code.BaseErrorCode;
import com.muffin.global.apiPayload.code.BaseSuccessCode;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"isSuccess", "code", "message", "result", "errorDetail"})
public class ApiResponse<T> {

    @Getter(AccessLevel.NONE)
    private final boolean success;

    private final String code;
    private final String message;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final T result;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<String> errorDetail;

    @JsonProperty("isSuccess")
    public boolean isSuccess() {
        return success;
    }

    public static <T> ApiResponse<T> onSuccess(BaseSuccessCode code, T result) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), result, null);
    }

    public static ApiResponse<Void> onSuccess(BaseSuccessCode code) {
        return new ApiResponse<>(true, code.getCode(), code.getMessage(), null, null);
    }

    public static <T> ApiResponse<T> onFailure(BaseErrorCode code, List<String> errorDetail) {
        List<String> safeErrorDetail = (errorDetail == null) ? null : List.copyOf(errorDetail);
        return new ApiResponse<>(false, code.getCode(), code.getMessage(), null, safeErrorDetail);
    }
}
