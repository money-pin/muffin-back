package com.muffin.investment.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record InvestmentRequest(
        @NotEmpty(message = "한 개 이상의 섹터를 선택해야 합니다.") List<@Valid InvestmentSectorRequest> sectors) {}
