package com.muffin.investment.domain.userasset;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserAssetRepositoryTest {

    @Autowired
    private UserAssetRepository userAssetRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("자정 마감 대상에는 활성 사용자의 자산만 포함한다")
    void findsActiveUserAssetsOnly() {
        User active = saveUser("active");
        User withdrawn = saveUser("gone");
        withdrawn.withdraw();
        User suspended = saveUser("stop");
        suspended.suspend();
        userRepository.flush();

        List<UserAsset> savedAssets = userAssetRepository.saveAllAndFlush(List.of(
                UserAsset.create(active.getUserId(), 1_000_000L),
                UserAsset.create(withdrawn.getUserId(), 1_000_000L),
                UserAsset.create(suspended.getUserId(), 1_000_000L)));
        LocalDateTime cutoff = savedAssets.stream()
                .map(UserAsset::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElseThrow()
                .plusSeconds(1);

        Slice<UserAsset> result = userAssetRepository.findByCreatedAtBeforeAndUserStatus(
                cutoff, UserStatus.ACTIVE, PageRequest.of(0, 500, Sort.by("id").ascending()));

        Set<Long> userIds =
                result.getContent().stream().map(UserAsset::getUserId).collect(Collectors.toSet());
        assertEquals(Set.of(active.getUserId()), userIds);
    }

    private User saveUser(String nickname) {
        return userRepository.saveAndFlush(User.register(null, UUID.randomUUID().toString(), "name", nickname));
    }
}
