package com.muffin.investment.presentation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InvestmentSectorRequest(
        @NotBlank(message = "섹터 코드는 필수입니다.") String sectorCode,
        @Min(value = 1, message = "수량은 1 이상이어야 합니다.") int quantity) {}
