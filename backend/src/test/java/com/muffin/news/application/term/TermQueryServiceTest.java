package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.SavedTermSort;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.response.SavedTermListResponse;
import com.muffin.news.presentation.dto.response.TermResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

class TermQueryServiceTest {

    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final UserSavedTermRepository userSavedTermRepository = mock(UserSavedTermRepository.class);
    private final SavedTermQueryRepository savedTermQueryRepository = mock(SavedTermQueryRepository.class);
    private final TermQueryService termQueryService =
            new TermQueryService(termDictionaryRepository, userSavedTermRepository, savedTermQueryRepository);

    @Test
    void getTerm_returnsTermDescriptionAndSavedStatus() {
        Long userId = 1L;
        Long termId = 10L;
        TermDictionary term = term(termId, "기준금리", "중앙은행이 정하는 대표 금리");

        when(termDictionaryRepository.findById(termId)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.existsByUserIdAndTermId(userId, termId)).thenReturn(true);

        TermResponse response = termQueryService.getTerm(userId, termId);

        assertThat(response.termId()).isEqualTo(termId);
        assertThat(response.term()).isEqualTo("기준금리");
        assertThat(response.content()).isEqualTo("중앙은행이 정하는 대표 금리");
        assertThat(response.isSaved()).isTrue();
    }

    @Test
    void getTerm_throwsWhenTermDoesNotExist() {
        when(termDictionaryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termQueryService.getTerm(1L, 999L))
                .isInstanceOf(NewsException.class)
                .hasMessage(NewsErrorCode.NEWS_TERM_NOT_FOUND.getMessage());
    }

    @Test
    void getSavedTerms_mapsSliceToResponse() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        SavedTermRow row = new SavedTermRow(12L, "기준금리", "설명", LocalDateTime.now());
        when(savedTermQueryRepository.findSavedTerms(eq(userId), eq(SavedTermSort.RECENT), any()))
                .thenReturn(new SliceImpl<>(List.of(row), pageable, false));

        SavedTermListResponse response = termQueryService.getSavedTerms(userId, pageable, null);

        assertThat(response.savedTerms()).hasSize(1);
        assertThat(response.savedTerms().get(0).termId()).isEqualTo(12L);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void getSavedTerms_resolvesAlphabeticalSort() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        when(savedTermQueryRepository.findSavedTerms(eq(userId), eq(SavedTermSort.ALPHABETICAL), any()))
                .thenReturn(new SliceImpl<>(List.of(), pageable, false));

        termQueryService.getSavedTerms(userId, pageable, "alphabetical");

        verify(savedTermQueryRepository).findSavedTerms(eq(userId), eq(SavedTermSort.ALPHABETICAL), any());
    }

    @Test
    void getSavedTerms_defaultsToRecentSortWhenSortParamMissing() {
        Long userId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        when(savedTermQueryRepository.findSavedTerms(eq(userId), eq(SavedTermSort.RECENT), any()))
                .thenReturn(new SliceImpl<>(List.of(), pageable, false));

        termQueryService.getSavedTerms(userId, pageable, null);

        verify(savedTermQueryRepository).findSavedTerms(eq(userId), eq(SavedTermSort.RECENT), any());
    }

    @Test
    void getSavedTerms_invalidSort_throwsIllegalArgumentException() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> termQueryService.getSavedTerms(1L, pageable, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getSavedTerms_sizeOver50_throwsBadRequest() {
        Pageable pageable = PageRequest.of(0, 51);

        assertThatThrownBy(() -> termQueryService.getSavedTerms(1L, pageable, null))
                .isInstanceOf(com.muffin.global.apiPayload.exception.GeneralException.class);
    }

    private TermDictionary term(Long termId, String term, String content) {
        TermDictionary dictionary = TermDictionary.create(term, content);
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }
}
