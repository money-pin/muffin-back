package com.muffin.investment.presentation.dto;

import java.time.LocalDate;
import java.util.List;

public record PreviousInvestmentResponse(
        LocalDate investDate, long totalAmount, List<TodayInvestmentSectorResponse> sectors) {

    public PreviousInvestmentResponse {
        sectors = List.copyOf(sectors);
    }
}
