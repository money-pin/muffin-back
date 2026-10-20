package com.muffin.notification.application.send;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NOTI-03 투자 결과 알림. 지정 투자일에 정산이 끝난 확정 투자마다 손익을 담아 보낸다.
 *
 * <p>정산 FAILED 건은 대상에 없다가, 재처리로 SETTLED가 된 뒤 다시 들어오는 정산 완료 이벤트에서 보내진다. 먼저 받은 사용자는 중복 방지 키가 거른다.
 */
@Service
@RequiredArgsConstructor
public class InvestmentResultNotificationService {

    private final InvestmentRepository investmentRepository;
    private final NotificationRecipientQueryRepository recipientQueryRepository;
    private final NotificationDispatcher dispatcher;

    public NotificationDispatchSummary notifySettled(LocalDate investDate) {
        List<Investment> settled = investmentRepository.findByInvestDateAndStatusAndSettlementStatus(
                investDate, InvestmentStatus.CONFIRMED, SettlementStatus.SETTLED);
        if (settled.isEmpty()) {
            return NotificationDispatchSummary.skipped("no_settled_investment");
        }

        Set<Long> recipients =
                new HashSet<>(recipientQueryRepository.findRecipientUserIds(NotificationType.INVESTMENT_RESULT));
        Map<Long, PushMessage> messages = new LinkedHashMap<>();
        for (Investment investment : settled) {
            if (recipients.contains(investment.getUserId())) {
                messages.put(
                        investment.getUserId(),
                        NotificationMessages.investmentResult(
                                investDate, investment.getTotalProfitLoss(), investment.getTotalAmount()));
            }
        }
        return dispatcher.dispatchAll(NotificationType.INVESTMENT_RESULT, investDate.toString(), messages);
    }
}
