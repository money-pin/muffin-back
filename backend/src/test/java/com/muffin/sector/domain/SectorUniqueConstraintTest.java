package com.muffin.sector.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 섹터 도메인의 주요 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SectorUniqueConstraintTest {

    @Autowired
    private SectorGroupRepository sectorGroupRepository;

    @Autowired
    private EtfRepository etfRepository;

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Test
    @DisplayName("같은 섹터 그룹 이름을 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void sectorGroup_duplicateName_violatesUnique() {
        sectorGroupRepository.saveAndFlush(SectorGroup.create("반도체", null));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sectorGroupRepository.saveAndFlush(SectorGroup.create("반도체", "다른 설명")));
    }

    @Test
    @DisplayName("같은 ETF 코드를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void etf_duplicateEtfCode_violatesUnique() {
        etfRepository.saveAndFlush(Etf.create("091160", "KODEX 반도체"));

        assertThrows(
                DataIntegrityViolationException.class, () -> etfRepository.saveAndFlush(Etf.create("091160", "다른 이름")));
    }

    @Test
    @DisplayName("같은 섹터 코드를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void sector_duplicateSectorCode_violatesUnique() {
        sectorRepository.saveAndFlush(Sector.create(1L, 10L, "반도체", null, "SEC-001"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sectorRepository.saveAndFlush(Sector.create(2L, 20L, "2차전지", null, "SEC-001")));
    }

    @Test
    @DisplayName("같은 ETF의 같은 날짜 시세를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void etfPrice_duplicateEtfAndPriceDate_violatesUnique() {
        LocalDate priceDate = LocalDate.of(2026, 7, 8);
        etfPriceRepository.saveAndFlush(EtfPrice.create(1L, priceDate, 10_000L, 10_500L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> etfPriceRepository.saveAndFlush(EtfPrice.create(1L, priceDate, 10_500L, 10_800L)));
    }
}
