package com.muffin.mypage.presentation.home.dto;

import java.time.DayOfWeek;

/** 마이페이지 홈 주간 활동(weeklyActivity) 응답에 쓰는 요일 표기. 일요일부터 시작한다. */
public enum WeekDay {
    SUN,
    MON,
    TUE,
    WED,
    THU,
    FRI,
    SAT;

    public static WeekDay from(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> SUN;
            case MONDAY -> MON;
            case TUESDAY -> TUE;
            case WEDNESDAY -> WED;
            case THURSDAY -> THU;
            case FRIDAY -> FRI;
            case SATURDAY -> SAT;
        };
    }
}
