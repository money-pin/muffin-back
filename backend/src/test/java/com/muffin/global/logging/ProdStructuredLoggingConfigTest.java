package com.muffin.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 운영 로그가 조용히 평문으로 돌아가는 사고를 막는다.
 *
 * <p>구조화 로깅은 설정 문자열로 연결돼 있어(포맷 이름, 커스터마이저 클래스명) 오타나 클래스 이동이 컴파일 오류로 잡히지 않는다. 그대로 배포되면 Loki에서 필드 조회가 전부
 * 깨지는데, 그 사실은 장애를 조사하려는 순간에야 드러난다.
 */
class ProdStructuredLoggingConfigTest {

    @Test
    @DisplayName("운영 프로파일은 콘솔 로그를 구조화 포맷으로 남긴다")
    void prodProfile_usesStructuredConsoleFormat() throws IOException {
        assertThat(prodProperty("logging.structured.format.console")).isEqualTo("logstash");
    }

    @Test
    @DisplayName("운영 프로파일이 지정한 커스터마이저는 실제로 존재하는 구현체다")
    void prodProfile_customizerClassExists() throws IOException, ClassNotFoundException {
        String className = String.valueOf(prodProperty("logging.structured.json.customizer"));

        Class<?> customizer = Class.forName(className);

        assertThat(StructuredLoggingJsonMembersCustomizer.class).isAssignableFrom(customizer);
    }

    private Object prodProperty(String name) throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application-prod", new ClassPathResource("application-prod.yml"));
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
