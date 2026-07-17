package com.muffin.investment.presentation.dto;

import java.math.BigDecimal;

public record TodayInvestmentSectorResponse(
        String sectorCode, String sectorName, int quantity, long amount, BigDecimal ratio) {}
