package com.muffin.sector.presentation.dto;

import java.util.List;

public record SectorGuideResponse(int totalCount, List<SectorGuideItem> sectors) {

    public record SectorGuideItem(
            String sectorCode, String name, String description, ReferenceAssetResponse referenceAsset) {}

    public record ReferenceAssetResponse(ReferenceAssetType type, String code, String name) {}

    public enum ReferenceAssetType {
        ETF,
        CRYPTO
    }
}
