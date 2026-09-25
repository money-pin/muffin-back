package com.muffin.sector.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.sector.application.SectorGuideQueryRepository;
import com.muffin.sector.application.projection.SectorGuideProjection;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, SectorGuideQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SectorGuideQueryRepositoryTest {

    @Autowired
    private SectorGuideQueryRepository queryRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private SectorGroupRepository groupRepository;

    @Autowired
    private EtfRepository etfRepository;

    @Test
    @DisplayName("활성 섹터를 그룹·섹터 순서로 정렬하고 실제 연결된 상품을 조회한다")
    void findActiveSectorGuides_filtersOrdersAndJoins() {
        SectorGroup future = groupRepository.save(SectorGroup.create("FUTURE_TECH", "미래", null, 2));
        SectorGroup base = groupRepository.save(SectorGroup.create("BASE_ASSET", "기초", null, 1));
        Etf btc = etfRepository.save(Etf.create("BTC", "비트코인"));
        Etf actual = etfRepository.save(Etf.create("CUSTOM", "실제 연결 상품"));
        // 기존 시드에 같은 섹터 코드의 상품이 있어도 sector.etf_id로 연결된 상품을 사용한다.
        etfRepository.save(Etf.create("132030", "KODEX 골드선물(H)"));
        sectorRepository.save(Sector.create(future.getId(), btc.getId(), "코인", null, "CRYPTO", 1));
        sectorRepository.save(Sector.create(base.getId(), actual.getId(), "금", "실제 DB 설명", "GOLD", 2));
        sectorRepository.save(Sector.create(base.getId(), actual.getId(), "예금", null, "DEPOSIT", 1));
        Sector inactive = Sector.create(base.getId(), actual.getId(), "달러", null, "USD", 0);
        inactive.deactivate();
        sectorRepository.save(inactive);

        List<SectorGuideProjection> result = queryRepository.findActiveSectorGuides();

        assertThat(result).extracting(SectorGuideProjection::sectorCode).containsExactly("DEPOSIT", "GOLD", "CRYPTO");
        assertThat(result.get(1)).isEqualTo(new SectorGuideProjection("GOLD", "금", "실제 DB 설명", "CUSTOM", "실제 연결 상품"));
        assertThat(result.get(2).assetCode()).isEqualTo("BTC");
        assertThat(result.get(2).description()).isNull();
    }

    @Test
    @DisplayName("표시 순서가 같으면 섹터 코드 순서로 결과를 고정한다")
    void findActiveSectorGuides_breaksOrderTies() {
        SectorGroup group = groupRepository.save(SectorGroup.create("BASE_ASSET", "기초", null, 1));
        Etf etf = etfRepository.save(Etf.create("CUSTOM", "상품"));
        sectorRepository.save(Sector.create(group.getId(), etf.getId(), "금", null, "GOLD", 1));
        sectorRepository.save(Sector.create(group.getId(), etf.getId(), "예금", null, "DEPOSIT", 1));

        assertThat(queryRepository.findActiveSectorGuides())
                .extracting(SectorGuideProjection::sectorCode)
                .containsExactly("DEPOSIT", "GOLD");
    }

    @Test
    @DisplayName("연결 상품이 없는 활성 섹터도 조회하여 서비스에서 매핑 누락을 감지한다")
    void findActiveSectorGuides_preservesMissingAsset() {
        SectorGroup group = groupRepository.save(SectorGroup.create("BASE_ASSET", "기초", null, 1));
        sectorRepository.save(Sector.create(group.getId(), Long.MAX_VALUE, "금", null, "GOLD", 1));

        assertThat(queryRepository.findActiveSectorGuides())
                .containsExactly(new SectorGuideProjection("GOLD", "금", null, null, null));
    }

    @Test
    @DisplayName("활성 섹터가 없으면 빈 목록을 조회한다")
    void findActiveSectorGuides_returnsEmptyList() {
        assertThat(queryRepository.findActiveSectorGuides()).isEmpty();
    }
}
