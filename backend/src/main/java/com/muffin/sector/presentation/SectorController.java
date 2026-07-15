package com.muffin.sector.presentation;

import com.muffin.global.apiPayload.ApiResponse;
import com.muffin.global.apiPayload.code.GeneralSuccessCode;
import com.muffin.sector.application.SectorQueryService;
import com.muffin.sector.presentation.dto.SectorListResponse;
import com.muffin.sector.presentation.swagger.SectorApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sectors")
@RequiredArgsConstructor
public class SectorController implements SectorApi {

    private final SectorQueryService sectorQueryService;

    @Override
    @GetMapping
    public ApiResponse<SectorListResponse> getSectors() {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, sectorQueryService.getAvailableSectors());
    }
}
