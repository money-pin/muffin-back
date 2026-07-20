package com.muffin.user.presentation.onboarding.dto;

import java.util.List;

public record CharacterResultResponse(
        Long characterId,
        String characterType,
        String characterName,
        String characterDescription,
        String imageUrl,
        List<RecommendedSectorResponse> recommendedSectors) {}
