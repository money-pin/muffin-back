package com.muffin.sector.application.projection;

public record SectorGuideProjection(
        String sectorCode,
        String name,
        String description,
        String assetCode,
        String assetName,
        String assetDescription) {}
