package com.muffin.sector.presentation.swagger;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.sector.presentation.dto.SectorGuideResponse;
import com.muffin.sector.presentation.dto.SectorListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Sector", description = "모의투자 섹터 API")
public interface SectorApi {

    @Operation(
            summary = "섹터별 기준 자산 안내 조회",
            description = "활성 섹터의 설명과 실제 연결된 기준 자산을 그룹·섹터 표시 순서대로 조회한다. "
                    + "referenceAsset.type은 ETF 또는 CRYPTO이며, BTC는 CRYPTO로 반환한다. "
                    + "활성 섹터가 없으면 totalCount는 0, sectors는 빈 배열이다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "기준 자산 매핑 누락(SECTOR_500_001)")
    })
    ApiResponse<SectorGuideResponse> getSectorGuide();

    @Operation(summary = "투자 가능 섹터 목록 조회", description = "활성 섹터를 그룹·섹터 표시 순서대로 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요(AUTH_401_001)")
    })
    ApiResponse<SectorListResponse> getSectors();
}
