package com.muffin.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BatchJobTest {

    @Test
    @DisplayName("잡 코드는 대시보드와 알림 룰이 참조하는 계약이므로 중복될 수 없다")
    void codes_areUnique() {
        long distinctCodes =
                Arrays.stream(BatchJob.values()).map(BatchJob::code).distinct().count();

        assertThat(distinctCodes).isEqualTo(BatchJob.values().length);
    }

    @Test
    @DisplayName("로거 이름은 잡의 도메인 패키지 아래에 놓여 로그의 domain 필드가 실제 도메인으로 채워진다")
    void loggerName_isUnderDomainPackage() {
        assertThat(BatchJob.SETTLEMENT.loggerName()).isEqualTo("com.muffin.investment.batch");
        assertThat(BatchJob.RSS_COLLECTION.loggerName()).isEqualTo("com.muffin.news.batch");
    }

    @Test
    @DisplayName("잡 코드는 로그 파싱이 깨지지 않도록 소문자와 밑줄만 사용한다")
    void codes_useSnakeCase() {
        String violations = Arrays.stream(BatchJob.values())
                .map(BatchJob::code)
                .filter(code -> !code.matches("[a-z0-9_]+"))
                .collect(Collectors.joining(", "));

        assertThat(violations).isEmpty();
    }
}
