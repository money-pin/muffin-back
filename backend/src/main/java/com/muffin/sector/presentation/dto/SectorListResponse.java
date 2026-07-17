package com.muffin.sector.presentation.dto;

import java.util.List;

public record SectorListResponse(long unitAmount, List<SectorGroupResponse> groups) {

    public record SectorGroupResponse(
            String groupCode, String groupName, int groupOrder, List<SectorResponse> sectors) {}

    public record SectorResponse(String sectorCode, String name, int sectorOrder) {}
}
