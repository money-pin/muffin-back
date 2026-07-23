package com.muffin.user.presentation.nickname;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.presentation.nickname.dto.NicknameChangeRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** 닉네임 변경 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NicknameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    private User createUser(String nickname) {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", nickname));
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("정상 변경 시 200과 함께 DB에 닉네임이 반영된다")
    void changeNickname_success() throws Exception {
        User user = createUser("원래닉네임");

        mockMvc.perform(patch("/api/mypage/nickname")
                        .header("Authorization", bearerTokenFor(user.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("새닉네임"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)))
                .andExpect(jsonPath("$.result.nickname", is("새닉네임")));

        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getNickname())
                .isEqualTo("새닉네임");
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void changeNickname_unauthorized() throws Exception {
        mockMvc.perform(patch("/api/mypage/nickname")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("새닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임이면 409 (USER_409_002)")
    void changeNickname_duplicated() throws Exception {
        createUser("이미있음");
        User me = createUser("내닉네임");

        mockMvc.perform(patch("/api/mypage/nickname")
                        .header("Authorization", bearerTokenFor(me.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("이미있음"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_409_002")));
    }

    @Test
    @DisplayName("2자 미만이면 400")
    void changeNickname_tooShort() throws Exception {
        User user = createUser("내닉네임");

        mockMvc.perform(patch("/api/mypage/nickname")
                        .header("Authorization", bearerTokenFor(user.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("일"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("비속어가 포함되면 400 (USER_400_001)")
    void changeNickname_profanity() throws Exception {
        User user = createUser("내닉네임");

        mockMvc.perform(patch("/api/mypage/nickname")
                        .header("Authorization", bearerTokenFor(user.getUserId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NicknameChangeRequest("개새끼닉네임"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("USER_400_001")));
    }
}
