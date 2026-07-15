package com.muffin.sector.application.seed;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 시딩 러너를 실제 DB(H2)에 두 번 실행해도 데이터가 중복 생성되지 않는지 검증한다.
 *
 * <p>{@link SectorSeedRunner}는 {@code @Profile("!test")}로 테스트 프로파일에서 자동 등록되지 않으므로, 스프링 빈으로
 * 주입받지 않고 직접 생성해서 테스트 트랜잭션(자동 롤백) 안에서만 실행한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SectorSeedRunnerIntegrationTest {

    @Autowired
    private SectorGroupRepository sectorGroupRepository;

    @Autowired
    private EtfRepository etfRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Test
    @DisplayName("두 번 연속 실행해도 그룹/ETF/섹터가 한 번씩만 생성된다")
    void run_isIdempotent() throws Exception {
        SectorSeedRunner sectorSeedRunner =
                new SectorSeedRunner(sectorGroupRepository, etfRepository, sectorRepository);

        sectorSeedRunner.run(new DefaultApplicationArguments());
        sectorSeedRunner.run(new DefaultApplicationArguments());

        assertEquals(SectorSeedData.GROUPS.size(), sectorGroupRepository.count());
        assertEquals(SectorSeedData.ETFS.size(), etfRepository.count());
        assertEquals(SectorSeedData.SECTORS.size(), sectorRepository.count());
    }
}
