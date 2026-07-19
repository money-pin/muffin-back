package com.muffin.character.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

/** 온보딩 설문 결과로 결정되는 머핀(캐릭터) 타입. */
public enum MuffinType {
    PLAIN,
    SPRINKLE,
    BUTTER;

    @JsonCreator
    public static MuffinType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("muffin 값은 필수입니다.");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("유효하지 않은 muffin 값입니다: " + value);
        }
    }
}
