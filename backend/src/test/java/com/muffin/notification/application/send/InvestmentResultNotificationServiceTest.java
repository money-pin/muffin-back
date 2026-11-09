package com.muffin.notification.application.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InvestmentResultNotificationServiceTest {

    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 10, 16);

    private final InvestmentRepository investmentRepository = mock(InvestmentRepository.class);
    private final NotificationRecipientQueryRepository recipientQueryRepository =
            mock(NotificationRecipientQueryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final InvestmentResultNotificationService service =
            new InvestmentResultNotificationService(investmentRepository, recipientQueryRepository, dispatcher);

    @Test
    @DisplayName("정산된 투자마다 그 사용자의 손익으로 문구를 만들고, 알림을 끈 사용자는 뺀다")
    @SuppressWarnings("unchecked")
    void sendsPerUserResult() {
        Investment profit = investment(1L, 12_345L, 300_000L);
        Investment muted = investment(2L, -5_000L, 200_000L);
        when(investmentRepository.findByInvestDateAndStatusAndSettlementStatus(
                        INVEST_DATE, InvestmentStatus.CONFIRMED, SettlementStatus.SETTLED))
                .thenReturn(List.of(profit, muted));
        when(recipientQueryRepository.findRecipientUserIds(NotificationType.INVESTMENT_RESULT))
                .thenReturn(List.of(1L));
        when(dispatcher.dispatchAll(any(), anyString(), anyMap())).thenReturn(NotificationDispatchSummary.empty());

        service.notifySettled(INVEST_DATE);

        ArgumentCaptor<Map<Long, PushMessage>> messages = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatchAll(eq(NotificationType.INVESTMENT_RESULT), eq("2026-10-16"), messages.capture());
        assertThat(messages.getValue())
                .containsExactly(Map.entry(1L, NotificationMessages.investmentResult(INVEST_DATE, 12_345L, 300_000L)));
    }

    @Test
    @DisplayName("정산된 투자가 없으면 보내지 않는다")
    void skipsWithoutSettledInvestment() {
        when(investmentRepository.findByInvestDateAndStatusAndSettlementStatus(
                        INVEST_DATE, InvestmentStatus.CONFIRMED, SettlementStatus.SETTLED))
                .thenReturn(List.of());

        NotificationDispatchSummary summary = service.notifySettled(INVEST_DATE);

        assertThat(summary.skipReason()).isEqualTo("no_settled_investment");
        verify(dispatcher, never()).dispatchAll(any(), anyString(), anyMap());
    }

    private static Investment investment(Long userId, long profitLoss, long amount) {
        Investment investment = mock(Investment.class);
        when(investment.getUserId()).thenReturn(userId);
        when(investment.getTotalProfitLoss()).thenReturn(profitLoss);
        when(investment.getTotalAmount()).thenReturn(amount);
        return investment;
    }
}
