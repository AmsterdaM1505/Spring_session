package ru.mephi.booking.service;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.mephi.shared.TraceIds;
import org.slf4j.MDC;

@Component
public class SagaRecovery {
    private final BookingTransactions transactions;
    private final BookingCoordinator coordinator;
    private final long pendingTimeout;
    public SagaRecovery(BookingTransactions transactions, BookingCoordinator coordinator,
    @Value("${saga.pending-timeout-seconds:60}") long pendingTimeout) {
        this.transactions = transactions;
        this.coordinator = coordinator;
        this.pendingTimeout = pendingTimeout;
    }
    @Scheduled(fixedDelayString = "${saga.recovery-delay-ms:5000}")
    public void recover() {
        for (UUID id : transactions.recoveryCandidates(Instant.now().minusSeconds(pendingTimeout))) {
            MDC.put("traceId", TraceIds.normalize(null));
            try {
                coordinator.compensate(transactions.failPending(id, "SAGA_EXPIRED"));
            }
            catch (RuntimeException ex) {
                LoggerFactory.getLogger(getClass()).atError().addKeyValue("bookingId", id)
                .addKeyValue("event", "recovery_failed").setCause(ex).log("Saga recovery failed");
            } finally
            {
                MDC.remove("traceId");
            }
        }
    }
}
