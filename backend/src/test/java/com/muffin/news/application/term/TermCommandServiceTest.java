package com.muffin.news.application.term;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.news.domain.term.UserSavedTerm;
import com.muffin.news.domain.term.UserSavedTermRepository;
import com.muffin.news.presentation.dto.TermSaveResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

class TermCommandServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long TERM_ID = 12L;

    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final UserSavedTermRepository userSavedTermRepository = mock(UserSavedTermRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final TermCommandService termCommandService =
            new TermCommandService(termDictionaryRepository, userSavedTermRepository, userRepository);

    @Test
    void saveTerm_savesWhenNotSavedYet() {
        TermDictionary term = term(TERM_ID, "기준금리");
        LocalDateTime savedAt = LocalDateTime.of(2026, 7, 26, 13, 30);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.findByUserIdAndTermId(USER_ID, TERM_ID)).thenReturn(Optional.empty());
        when(userSavedTermRepository.save(any(UserSavedTerm.class))).thenAnswer(invocation -> {
            UserSavedTerm savedTerm = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedTerm, "id", 100L);
            ReflectionTestUtils.setField(savedTerm, "savedAt", savedAt);
            return savedTerm;
        });

        TermSaveResponse response = termCommandService.saveTerm(USER_ID, TERM_ID);

        assertThat(response.termId()).isEqualTo(TERM_ID);
        assertThat(response.term()).isEqualTo("기준금리");
        assertThat(response.isSaved()).isTrue();
        assertThat(response.savedAt()).isEqualTo(savedAt);
        verify(userSavedTermRepository).save(any(UserSavedTerm.class));
    }

    @Test
    void saveTerm_returnsExistingSavedStatusWhenAlreadySaved() {
        TermDictionary term = term(TERM_ID, "기준금리");
        UserSavedTerm existing = savedTerm(USER_ID, TERM_ID, LocalDateTime.of(2026, 7, 25, 10, 0));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.findByUserIdAndTermId(USER_ID, TERM_ID)).thenReturn(Optional.of(existing));

        TermSaveResponse response = termCommandService.saveTerm(USER_ID, TERM_ID);

        assertThat(response.isSaved()).isTrue();
        assertThat(response.savedAt()).isEqualTo(existing.getSavedAt());
        verify(userSavedTermRepository, never()).save(any(UserSavedTerm.class));
    }

    @Test
    void saveTerm_returnsExistingSavedStatusWhenConcurrentInsertWins() {
        TermDictionary term = term(TERM_ID, "기준금리");
        UserSavedTerm existing = savedTerm(USER_ID, TERM_ID, LocalDateTime.of(2026, 7, 25, 10, 0));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.findByUserIdAndTermId(USER_ID, TERM_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(userSavedTermRepository.save(any(UserSavedTerm.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_saved_term_user_term"));

        TermSaveResponse response = termCommandService.saveTerm(USER_ID, TERM_ID);

        assertThat(response.isSaved()).isTrue();
        assertThat(response.savedAt()).isEqualTo(existing.getSavedAt());
    }

    @Test
    void saveTerm_throwsWhenTermDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termCommandService.saveTerm(USER_ID, TERM_ID))
                .isInstanceOf(NewsException.class)
                .hasMessage(NewsErrorCode.NEWS_TERM_NOT_FOUND.getMessage());
    }

    @Test
    void saveTerm_throwsForbiddenWhenOnboardingIsNotCompleted() {
        User user = User.register(null, "00000000-0000-0000-0000-000000000001", "세현", "세현");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> termCommandService.saveTerm(USER_ID, TERM_ID))
                .isInstanceOf(GeneralException.class)
                .extracting("errorCode")
                .isEqualTo(GeneralErrorCode.FORBIDDEN);
    }

    @Test
    void unsaveTerm_deletesWhenSaved() {
        TermDictionary term = term(TERM_ID, "기준금리");
        UserSavedTerm existing = savedTerm(USER_ID, TERM_ID, LocalDateTime.of(2026, 7, 25, 10, 0));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.findByUserIdAndTermId(USER_ID, TERM_ID)).thenReturn(Optional.of(existing));

        TermSaveResponse response = termCommandService.unsaveTerm(USER_ID, TERM_ID);

        assertThat(response.termId()).isEqualTo(TERM_ID);
        assertThat(response.term()).isEqualTo("기준금리");
        assertThat(response.isSaved()).isFalse();
        assertThat(response.savedAt()).isNull();
        verify(userSavedTermRepository).delete(existing);
    }

    @Test
    void unsaveTerm_returnsUnsavedStatusWhenAlreadyUnsaved() {
        TermDictionary term = term(TERM_ID, "기준금리");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.of(term));
        when(userSavedTermRepository.findByUserIdAndTermId(USER_ID, TERM_ID)).thenReturn(Optional.empty());

        TermSaveResponse response = termCommandService.unsaveTerm(USER_ID, TERM_ID);

        assertThat(response.isSaved()).isFalse();
        assertThat(response.savedAt()).isNull();
        verify(userSavedTermRepository, never()).delete(any(UserSavedTerm.class));
    }

    @Test
    void unsaveTerm_throwsWhenTermDoesNotExist() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(onboardingCompletedUser()));
        when(termDictionaryRepository.findById(TERM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> termCommandService.unsaveTerm(USER_ID, TERM_ID))
                .isInstanceOf(NewsException.class)
                .hasMessage(NewsErrorCode.NEWS_TERM_NOT_FOUND.getMessage());
    }

    private User onboardingCompletedUser() {
        User user = User.register(null, "00000000-0000-0000-0000-000000000001", "세현", "세현");
        user.completeOnboarding(1, 2, 3);
        return user;
    }

    private TermDictionary term(Long termId, String term) {
        TermDictionary dictionary = TermDictionary.create(term, term + " 설명");
        ReflectionTestUtils.setField(dictionary, "id", termId);
        return dictionary;
    }

    private UserSavedTerm savedTerm(Long userId, Long termId, LocalDateTime savedAt) {
        UserSavedTerm savedTerm = UserSavedTerm.create(userId, termId);
        ReflectionTestUtils.setField(savedTerm, "id", 100L);
        ReflectionTestUtils.setField(savedTerm, "savedAt", savedAt);
        return savedTerm;
    }
}
