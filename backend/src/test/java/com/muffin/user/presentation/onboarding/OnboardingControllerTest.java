package com.muffin.user.presentation.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.character.domain.characterprofile.CharacterProfile;
import com.muffin.character.domain.characterprofile.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 온보딩 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private UserAssetRepository userAssetRepository;

    @AfterEach
    void cleanUp() {
        userAssetRepository.deleteAll();
        userRepository.deleteAll();
        characterRepository.deleteAll();
    }

    private User createUser() {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
    }

    private void seedPlainCharacter() {
        characterRepository.save(CharacterProfile.create(
                MuffinType.PLAIN, "플레인 머핀", "기본에 충실한 안정형 캐릭터", "https://example.com/plain.png"));
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("유효하지 않은 muffin 값이면 서비스 계층까지 가지 않고 역직렬화 단계에서 400을 반환한다")
    void characterResult_invalidMuffinType_returns400() throws Exception {
        User user = createUser();

        mockMvc.perform(
                        post("/api/onboarding/character")
                                .header("Authorization", bearerTokenFor(user.getUserId()))
                                .contentType("application/json")
                                .content(
                                        "{\"muffin\":\"chocolate\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess", is(false)));
    }

    @Test
    @DisplayName("정상 요청이면 200, 캐릭터/온보딩이 확정된다")
    void characterResult_success() throws Exception {
        User user = createUser();
        seedPlainCharacter();

        mockMvc.perform(post("/api/onboarding/character")
                        .header("Authorization", bearerTokenFor(user.getUserId()))
                        .contentType("application/json")
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.characterType", is("PLAIN")));

        User updated = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(updated.isOnboardingCompleted()).isTrue();
        assertThat(updated.getCharacterId()).isNotNull();
    }

    @Test
    @DisplayName("인증 헤더가 없으면 401")
    void characterResult_unauthorized_returns401() throws Exception {
        mockMvc.perform(post("/api/onboarding/character")
                        .contentType("application/json")
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("캐릭터 결과 저장 전에 완료 API를 호출하면 409 (USER_409_001)")
    void complete_notCompleted_returns409() throws Exception {
        User user = createUser();

        mockMvc.perform(post("/api/onboarding/complete").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_409_001")));
    }

    @Test
    @DisplayName("완료 후 초기자산이 지급되고, 재호출해도 같은 자산을 그대로 반환한다(멱등)")
    void complete_success_and_idempotent() throws Exception {
        User user = createUser();
        seedPlainCharacter();
        String bearer = bearerTokenFor(user.getUserId());
        mockMvc.perform(post("/api/onboarding/character")
                        .header("Authorization", bearer)
                        .contentType("application/json")
                        .content("{\"muffin\":\"plain\",\"firstQuestion\":1,\"secondQuestion\":2,\"thirdQuestion\":3}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/onboarding/complete").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAsset", is(1_000_000)));

        mockMvc.perform(post("/api/onboarding/complete").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalAsset", is(1_000_000)));

        assertThat(userAssetRepository.findByUserId(user.getUserId())).isPresent();
    }
}
