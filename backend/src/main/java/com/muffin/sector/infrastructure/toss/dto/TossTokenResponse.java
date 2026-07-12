package com.muffin.sector.infrastructure.toss.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code POST /oauth2/token} 응답. */
public record TossTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {}
