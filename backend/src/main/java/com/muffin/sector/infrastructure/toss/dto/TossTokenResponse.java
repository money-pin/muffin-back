package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code POST /oauth2/token} 응답. BFF 공통 envelope이 아닌 OAuth2 표준 형식(플랫)이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {}
