package com.muffin.investment.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TodayInvestmentResponse(
        TodayInvestmentStatus status,
        OffsetDateTime confirmDeadline,
        Long remainingAmount,
        Long totalAmount,
        List<TodayInvestmentSectorResponse> sectors,
        OffsetDateTime nextInvestmentAvailableAt,
        PreviousInvestmentResponse previousInvestment) {

    public static TodayInvestmentResponse available(long totalAsset) {
        return new TodayInvestmentResponse(
                TodayInvestmentStatus.AVAILABLE, null, totalAsset, 0L, List.of(), null, null);
    }

    public static TodayInvestmentResponse confirmed(
            OffsetDateTime confirmDeadline,
            long remainingAmount,
            long totalAmount,
            List<TodayInvestmentSectorResponse> sectors) {
        return new TodayInvestmentResponse(
                TodayInvestmentStatus.CONFIRMED_EDITABLE,
                confirmDeadline,
                remainingAmount,
                totalAmount,
                List.copyOf(sectors),
                null,
                null);
    }

    public static TodayInvestmentResponse unavailable(OffsetDateTime nextInvestmentAvailableAt) {
        return unavailable(nextInvestmentAvailableAt, null);
    }

    public static TodayInvestmentResponse unavailable(
            OffsetDateTime nextInvestmentAvailableAt, PreviousInvestmentResponse previousInvestment) {
        return new TodayInvestmentResponse(
                TodayInvestmentStatus.UNAVAILABLE,
                null,
                null,
                null,
                null,
                nextInvestmentAvailableAt,
                previousInvestment);
    }

    public static TodayInvestmentResponse settling() {
        return settling(null);
    }

    public static TodayInvestmentResponse settling(PreviousInvestmentResponse previousInvestment) {
        return new TodayInvestmentResponse(
                TodayInvestmentStatus.SETTLING, null, null, null, null, null, previousInvestment);
    }

    public static TodayInvestmentResponse delayed() {
        return statusOnly(TodayInvestmentStatus.SETTLEMENT_DELAYED);
    }

    public static TodayInvestmentResponse closedWeekend(OffsetDateTime nextInvestmentAvailableAt) {
        return statusWithNext(TodayInvestmentStatus.CLOSED_WEEKEND, nextInvestmentAvailableAt);
    }

    public static TodayInvestmentResponse closedHoliday(OffsetDateTime nextInvestmentAvailableAt) {
        return statusWithNext(TodayInvestmentStatus.CLOSED_HOLIDAY, nextInvestmentAvailableAt);
    }

    private static TodayInvestmentResponse statusOnly(TodayInvestmentStatus status) {
        return new TodayInvestmentResponse(status, null, null, null, null, null, null);
    }

    private static TodayInvestmentResponse statusWithNext(
            TodayInvestmentStatus status, OffsetDateTime nextInvestmentAvailableAt) {
        return new TodayInvestmentResponse(status, null, null, null, null, nextInvestmentAvailableAt, null);
    }
}
