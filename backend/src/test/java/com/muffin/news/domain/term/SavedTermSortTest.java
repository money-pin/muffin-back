package com.muffin.news.domain.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SavedTermSortTest {

    @Test
    void from_null_returnsDefault() {
        assertThat(SavedTermSort.from(null)).isEqualTo(SavedTermSort.RECENT);
    }

    @Test
    void from_blank_returnsDefault() {
        assertThat(SavedTermSort.from("  ")).isEqualTo(SavedTermSort.RECENT);
    }

    @Test
    void from_recent_caseInsensitive() {
        assertThat(SavedTermSort.from("recent")).isEqualTo(SavedTermSort.RECENT);
        assertThat(SavedTermSort.from("RECENT")).isEqualTo(SavedTermSort.RECENT);
    }

    @Test
    void from_alphabetical_caseInsensitive() {
        assertThat(SavedTermSort.from("alphabetical")).isEqualTo(SavedTermSort.ALPHABETICAL);
        assertThat(SavedTermSort.from("Alphabetical")).isEqualTo(SavedTermSort.ALPHABETICAL);
    }

    @Test
    void from_invalidValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> SavedTermSort.from("popular")).isInstanceOf(IllegalArgumentException.class);
    }
}
