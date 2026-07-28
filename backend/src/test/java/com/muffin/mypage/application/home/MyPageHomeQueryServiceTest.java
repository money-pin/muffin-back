package com.muffin.mypage.application.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.muffin.character.domain.CharacterProfile;
import com.muffin.character.domain.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.mypage.presentation.home.dto.MyPageHomeResponse;
import com.muffin.news.application.query.NewsQueryRepository;
import com.muffin.news.application.query.RecentReadNewsRow;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.exception.UserErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MyPageHomeQueryServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Mock
    private UserRepository userRepository;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private QuizSessionRepository quizSessionRepository;

    @Mock
    private NewsQueryRepository newsQueryRepository;

    private final Clock clock =
            Clock.fixed(LocalDate.of(2026, 7, 23).atStartOfDay(KST).toInstant(), KST);

    private MyPageHomeQueryService myPageHomeQueryService;

    @BeforeEach
    void setUp() {
        myPageHomeQueryService = new MyPageHomeQueryService(
                userRepository, characterRepository, quizSessionRepository, newsQueryRepository, clock);
    }

    @Test
    @DisplayName("닉네임/캐릭터/스트릭/최근 뉴스를 조합해서 응답한다")
    void getHome_success() {
        User user = User.register(1L, UUID.randomUUID().toString(), "홍길동", "길동이");
        CharacterProfile character = CharacterProfile.create(MuffinType.PLAIN, "플레인 머핀", "설명", "http://image");
        ReflectionTestUtils.setField(character, "characterId", 1L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(1L)).thenReturn(Optional.of(character));
        when(quizSessionRepository.findDatesByUserIdAndStatus(USER_ID, QuizSessionStatus.FINISHED))
                .thenReturn(List.of(LocalDate.of(2026, 7, 23)));
        when(newsQueryRepository.findRecentReadNews(any(), anyInt()))
                .thenReturn(List.of(new RecentReadNewsRow(10L, "제목", "http://thumb", LocalDateTime.now())));

        MyPageHomeResponse response = myPageHomeQueryService.getHome(USER_ID);

        assertThat(response.nickname()).isEqualTo("길동이");
        assertThat(response.character().characterId()).isEqualTo(1L);
        assertThat(response.character().characterType()).isEqualTo(MuffinType.PLAIN);
        assertThat(response.streak().currentStreak()).isEqualTo(1);
        assertThat(response.recentNews()).hasSize(1);
        assertThat(response.recentNews().get(0).newsId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("존재하지 않는 유저 → USER_NOT_FOUND")
    void getHome_userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageHomeQueryService.getHome(USER_ID))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex ->
                        assertThat(((GeneralException) ex).getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("온보딩 미완료(characterId=null) → ONBOARDING_NOT_COMPLETED")
    void getHome_onboardingNotCompleted() {
        User user = User.register(null, UUID.randomUUID().toString(), "홍길동", "길동이");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> myPageHomeQueryService.getHome(USER_ID))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.ONBOARDING_NOT_COMPLETED));
    }

    @Test
    @DisplayName("characterId는 있지만 실제 캐릭터가 없으면 → CHARACTER_NOT_FOUND")
    void getHome_characterNotFound() {
        User user = User.register(1L, UUID.randomUUID().toString(), "홍길동", "길동이");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(characterRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myPageHomeQueryService.getHome(USER_ID))
                .isInstanceOf(GeneralException.class)
                .satisfies(ex -> assertThat(((GeneralException) ex).getErrorCode())
                        .isEqualTo(UserErrorCode.CHARACTER_NOT_FOUND));
    }
}
