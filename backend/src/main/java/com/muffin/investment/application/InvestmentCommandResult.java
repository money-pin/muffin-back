package com.muffin.investment.application;

import com.muffin.investment.presentation.dto.TodayInvestmentResponse;

public record InvestmentCommandResult(TodayInvestmentResponse response, boolean created) {}
