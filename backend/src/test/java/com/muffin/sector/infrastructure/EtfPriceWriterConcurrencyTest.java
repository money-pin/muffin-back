package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** {@code EtfPriceWriter}가 동시 저장 경쟁(유니크 제약 위반) 상황에서 복구하는지 리포지토리를 목킹해 검증한다. */
@ExtendWith(MockitoExtension.class)
class EtfPriceWriterConcurrencyTest {

    private static final Long ETF_ID = 1L;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 10);

    @Mock
    private EtfPriceRepository etfPriceRepository;

    private EtfPriceWriter etfPriceWriter;

    @BeforeEach
    void setUp() {
        etfPriceWriter = new EtfPriceWriter(etfPriceRepository);
    }

    @Test
    @DisplayName("새로 저장하려는 사이 다른 트랜잭션이 먼저 커밋되면, 예외 대신 그 레코드를 다시 조회해 갱신한다")
    void writeOpen_recoversFromRaceCondition() {
        EtfPrice raceWinner = EtfPrice.open(ETF_ID, PRICE_DATE, 9_000L);
        when(etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raceWinner));
        when(etfPriceRepository.saveAndFlush(any(EtfPrice.class)))
                .thenThrow(new DataIntegrityViolationException("uk_etf_price_etf_price_date"));

        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        assertEquals(10_000L, raceWinner.getStartPrice());
        verify(etfPriceRepository, times(2)).findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE);
    }

    @Test
    @DisplayName("경쟁에서 진 뒤 재조회해도 레코드가 없으면 원래 예외를 다시 던진다")
    void writeOpen_rethrowsOriginalException_whenRecoveryFindsNothing() {
        when(etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE)).thenReturn(Optional.empty());
        DataIntegrityViolationException original = new DataIntegrityViolationException("uk_etf_price_etf_price_date");
        when(etfPriceRepository.saveAndFlush(any(EtfPrice.class))).thenThrow(original);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class, () -> etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L));

        assertSame(original, thrown);
    }
}
