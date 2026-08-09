package com.muffin.investment.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 사용자 단위 유일성(사용자-투자일자, 사용자-요약일자, 사용자 1:1) DB 유니크 제약 검증. 도메인 순수 단위 테스트로는 확인할 수 없어 실제 저장을 시도한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UniqueConstraintTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 7);

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserAssetRepository userAssetRepository;

    @Test
    @DisplayName("같은 사용자-투자일자로 투자를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void investment_duplicateUserAndInvestDate_violatesUnique() {
        investmentRepository.saveAndFlush(Investment.confirm(1L, DATE));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> investmentRepository.saveAndFlush(Investment.confirm(1L, DATE)));
    }

    @Test
    @DisplayName("한 투자에 같은 섹터를 두 번 담으면 유니크 제약 위반이 발생한다")
    void investmentSector_duplicateSectorInSameInvestment_violatesUnique() {
        // 루트의 전체 교체 로직은 중복을 만들지 않지만, 저장 경로가 늘어나도 DB가 막는지 확인한다.
        Investment investment = Investment.confirm(2L, DATE);
        investment.addSector(100L, 10, 300_000L, null);
        investment.addSector(100L, 5, 150_000L, null);

        assertThrows(DataIntegrityViolationException.class, () -> investmentRepository.saveAndFlush(investment));
    }

    @Test
    @DisplayName("같은 사용자로 자산을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void userAsset_duplicateUser_violatesUnique() {
        userAssetRepository.saveAndFlush(UserAsset.create(1L, 1_000_000L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userAssetRepository.saveAndFlush(UserAsset.create(1L, 2_000_000L)));
    }
}
