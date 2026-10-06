package com.muffin.briefing.presentation.swagger;

import com.muffin.briefing.presentation.dto.BriefingDateListResponse;
import com.muffin.briefing.presentation.dto.BriefingResponse;
import com.muffin.briefing.presentation.dto.BriefingViewResponse;
import com.muffin.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;

@Tag(name = "브리핑", description = "모닝 머핀 브리핑 API")
public interface BriefingApi {

    @Operation(
            summary = "오늘의 브리핑 조회",
            description =
                    """
                    오늘 발행된 브리핑을 조회한다. 오늘 발행분이 없으면 최근 발행분을 대신 내려보내며 isToday가 false가 된다.
                    아직 생성 중이거나 생성에 실패했으면 status(GENERATING, UNAVAILABLE)만 채워 내려보낸다.
                    marketIndices는 시장 지표 수집이 붙기 전까지 항상 빈 배열이다.
                    """)
    ApiResponse<BriefingResponse> getTodayBriefing();

    @Operation(summary = "특정 날짜 브리핑 조회", description = "다시 보기 범위를 벗어난 날짜는 400으로 거절한다.")
    ApiResponse<BriefingResponse> getBriefing(LocalDate briefingDate);

    @Operation(summary = "최근 브리핑 날짜 목록", description = "날짜 선택 칩에 쓸 최근 브리핑 목록을 최신순으로 반환한다.")
    ApiResponse<BriefingDateListResponse> getRecentBriefingDates();

    @Operation(summary = "브리핑 열람 기록", description = "열람 시각을 저장한다. 다시 열람하면 시각만 갱신한다.")
    ApiResponse<BriefingViewResponse> recordBriefingView(Long userId, LocalDate briefingDate);
}
