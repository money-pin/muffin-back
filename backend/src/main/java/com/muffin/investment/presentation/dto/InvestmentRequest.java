package com.muffin.investment.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InvestmentRequest(
        @NotEmpty(message = "한 개 이상의 섹터를 선택해야 합니다.") List<@NotNull(message = "섹터 정보는 필수입니다.") @Valid InvestmentSectorRequest> sectors) {}
