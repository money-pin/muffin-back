package com.muffin.stats.application.projection;

import java.time.LocalDate;

/** 정산 완료된 일자별 손익 조회 결과. (user_id, invest_date) 유니크라 날짜당 1건이다. */
public record DailyProfitProjection(LocalDate investDate, Long dailyProfitLoss) {}
