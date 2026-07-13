package com.muffin.sector.application.seed;

import com.muffin.sector.application.seed.SectorSeedData.EtfSeed;
import com.muffin.sector.application.seed.SectorSeedData.GroupSeed;
import com.muffin.sector.application.seed.SectorSeedData.SectorSeed;
import com.muffin.sector.domain.etf.Etf;
import com.muffin.sector.domain.etf.EtfRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 애플리케이션 기동 시 섹터/ETF 기준 데이터를 멱등하게 채워 넣는다. 이미 존재하는 코드는 건너뛰므로 여러 번 실행해도 안전하다.
 *
 * <p>{@code test} 프로파일에서는 실행하지 않는다. 테스트는 매번 빈 스키마로 시작한다고 가정하므로, 여기서 실제로 커밋되는
 * 시딩이 끼어들면 같은 예시 코드를 사용하는 다른 테스트(UNIQUE 제약 테스트 등)와 충돌한다.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class SectorSeedRunner implements ApplicationRunner {

    private final SectorGroupRepository sectorGroupRepository;
    private final EtfRepository etfRepository;
    private final SectorRepository sectorRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Map<String, Long> groupIdsByCode = seedGroups();
        Map<String, Long> etfIdsByCode = seedEtfs();
        seedSectors(groupIdsByCode, etfIdsByCode);
    }

    private Map<String, Long> seedGroups() {
        Map<String, Long> groupIdsByCode = new HashMap<>();
        for (GroupSeed seed : SectorSeedData.GROUPS) {
            SectorGroup group = sectorGroupRepository
                    .findByGroupCode(seed.groupCode())
                    .orElseGet(() -> sectorGroupRepository.save(
                            SectorGroup.create(seed.groupCode(), seed.name(), seed.description(), seed.groupOrder())));
            groupIdsByCode.put(seed.groupCode(), group.getId());
        }
        log.info("섹터 그룹 시딩 완료: 대상 {}건", SectorSeedData.GROUPS.size());
        return groupIdsByCode;
    }

    private Map<String, Long> seedEtfs() {
        Map<String, Long> etfIdsByCode = new HashMap<>();
        for (EtfSeed seed : SectorSeedData.ETFS) {
            Etf etf = etfRepository
                    .findByEtfCode(seed.etfCode())
                    .orElseGet(() -> etfRepository.save(Etf.create(seed.etfCode(), seed.etfName())));
            etfIdsByCode.put(seed.etfCode(), etf.getId());
        }
        log.info("ETF 시딩 완료: 대상 {}건", SectorSeedData.ETFS.size());
        return etfIdsByCode;
    }

    private void seedSectors(Map<String, Long> groupIdsByCode, Map<String, Long> etfIdsByCode) {
        for (SectorSeed seed : SectorSeedData.SECTORS) {
            sectorRepository.findBySectorCode(seed.sectorCode()).orElseGet(() -> {
                Long sectorGroupId = groupIdsByCode.get(seed.groupCode());
                Long etfId = etfIdsByCode.get(seed.etfCode());
                return sectorRepository.save(Sector.create(
                        sectorGroupId, etfId, seed.name(), seed.description(), seed.sectorCode(), seed.sectorOrder()));
            });
        }
        log.info("섹터 시딩 완료: 대상 {}건", SectorSeedData.SECTORS.size());
    }
}
