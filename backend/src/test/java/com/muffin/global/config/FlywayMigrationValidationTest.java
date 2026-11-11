package com.muffin.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** DB 연결 없이 운영과 같은 SQL 위치의 Flyway 버전 중복을 빌드 단계에서 검증한다. */
class FlywayMigrationValidationTest {

    private static final Pattern VERSIONED_MIGRATION = Pattern.compile("^V(.+?)__(.+)\\.sql$");

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("설정된 Flyway SQL 위치 전체에 중복 버전이 없다")
    void configuredMigrationVersionsAreUnique() throws IOException {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        yaml.afterPropertiesSet();
        var configuration = yaml.getObject();
        assertThat(configuration).isNotNull();

        var locations = new Binder(new MapConfigurationPropertySource(configuration))
                .bind("spring.flyway.locations", Bindable.listOf(String.class))
                .orElse(List.of("classpath:db/migration"));
        var migrations = migrationResources(locations);

        assertThat(migrations).as("설정된 Flyway 위치의 SQL 파일").isNotEmpty();
        assertThat(duplicateVersions(migrations))
                .as("Flyway 중복 버전과 파일 경로 (적용된 마이그레이션은 보존하고 새 파일의 버전을 변경)")
                .isEmpty();
    }

    @Test
    @DisplayName("migration과 seed 하위 디렉터리 사이의 중복 버전을 탐지한다")
    void detectsDuplicatesAcrossNestedLocations() throws IOException {
        writeMigration("db/migration/nested/V10__schema.sql");
        writeMigration("db/seed/V10__seed.sql");

        var duplicates = duplicateVersions(migrationResources(List.of(location("db"))));

        assertThat(duplicates).singleElement().asString().contains("V10", "V10__schema.sql", "V10__seed.sql");
    }

    @ParameterizedTest
    @ValueSource(strings = {"010", "10.0", "10_0", "10.00.0"})
    @DisplayName("Flyway가 같은 버전으로 취급하는 표현도 중복으로 탐지한다")
    void detectsEquivalentVersions(String equivalentVersion) throws IOException {
        writeMigration("V10__first.sql");
        String equivalentFilename = "V" + equivalentVersion + "__second.sql";
        writeMigration(equivalentFilename);

        var duplicates = duplicateVersions(migrationResources(List.of(location(""))));

        assertThat(duplicates).singleElement().asString().contains("V10__first.sql", equivalentFilename);
    }

    @Test
    @DisplayName("서로 다른 버전과 반복 마이그레이션은 허용한다")
    void permitsDistinctVersionsAndRepeatableMigrations() throws IOException {
        writeMigration("V1__first.sql");
        writeMigration("V1_1__second.sql");
        writeMigration("V2__description__with_separator.sql");
        writeMigration("R__seed_category.sql");
        writeMigration("R__seed_other.sql");

        assertThat(duplicateVersions(migrationResources(List.of(location(""))))).isEmpty();
    }

    @Test
    @DisplayName("설정 위치가 겹쳐도 같은 SQL 파일을 중복 계산하지 않는다")
    void scansOverlappingLocationsOnce() throws IOException {
        writeMigration("db/migration/V1__first.sql");

        var migrations = migrationResources(List.of(location("db"), location("db/migration")));

        assertThat(migrations).hasSize(1);
        assertThat(duplicateVersions(migrations)).isEmpty();
    }

    private static List<Resource> migrationResources(List<String> locations) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        Map<String, Resource> migrations = new LinkedHashMap<>();
        for (String location : locations) {
            String root = location.replaceFirst("^classpath:", "classpath*:").replaceAll("/+$", "");
            for (Resource migration : resolver.getResources(root + "/**/*.sql")) {
                migrations.putIfAbsent(migration.getURI().normalize().toString(), migration);
            }
        }
        return new ArrayList<>(migrations.values());
    }

    private static List<String> duplicateVersions(List<Resource> migrations) throws IOException {
        Map<MigrationVersion, List<String>> versions = new TreeMap<>();
        for (Resource migration : migrations) {
            var filename = migration.getFilename();
            if (filename == null) {
                continue;
            }
            var matcher = VERSIONED_MIGRATION.matcher(filename);
            if (matcher.matches()) {
                var version = MigrationVersion.fromVersion(matcher.group(1));
                versions.computeIfAbsent(version, ignored -> new ArrayList<>())
                        .add(migration.getURI().toString());
            }
        }
        return versions.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> "V" + entry.getKey() + ": " + String.join(", ", entry.getValue()))
                .toList();
    }

    private void writeMigration(String relativePath) throws IOException {
        Path migration = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(migration.getParent());
        Files.writeString(migration, "SELECT 1;\n");
    }

    private String location(String relativePath) {
        return temporaryDirectory.resolve(relativePath).toUri().toString();
    }
}
