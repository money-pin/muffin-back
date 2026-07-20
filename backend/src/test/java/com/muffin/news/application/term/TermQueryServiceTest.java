package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.response.TermResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TermQueryServiceTest {

    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final UserSavedTermRepository userSavedTermRepository = mock(UserSavedTermRepository.class);
    private final TermQueryService termQueryService =
            new TermQueryService(termDictionaryRepository, userSavedTermRepository);

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

    private TermDictionary term(Long termId, String term, String content) {
        TermDictionary dictionary = TermDictionary.create(term, content);
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }
}
