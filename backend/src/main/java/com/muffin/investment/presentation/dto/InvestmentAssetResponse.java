package com.muffin.investment.presentation.dto;

import java.math.BigDecimal;

public record InvestmentAssetResponse(
        long totalAsset,
        long dailyChangeAmount,
        BigDecimal dailyChangeRate,
        AssetChangeDirection changeDirection,
        boolean settlementPending) {}
