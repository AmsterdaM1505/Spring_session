package ru.mephi.booking.integration;

import io.github.resilience4j.circuitbreaker.*;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.*;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.client.*;
import ru.mephi.booking.api.BookingDtos.BookingView;
import ru.mephi.booking.security.TokenService;
import ru.mephi.shared.*;

public class HotelHttpClient implements HotelPort {
    private final RestClient client;
    private final TokenService tokens;
    private final Retry retry;
    private final CircuitBreaker breaker;

    public HotelHttpClient(RestClient client, TokenService tokens, int maxAttempts, long delayMillis) {
        this.client = client;
        this.tokens = tokens;
        retry = Retry.of("hotel", RetryConfig.custom().maxAttempts(maxAttempts)
        .intervalFunction(IntervalFunction.ofExponentialBackoff(delayMillis, 2))
        .retryOnException(ex -> ex instanceof RemoteFailure).build());
        breaker = CircuitBreaker.of("hotel", CircuitBreakerConfig.custom().slidingWindowSize(10)
        .minimumNumberOfCalls(5).failureRateThreshold(60).waitDurationInOpenState(Duration.ofSeconds(10))
        .recordException(ex -> ex instanceof RemoteFailure)
        .ignoreException(ex -> ex instanceof DomainException).build());
        retry.getEventPublisher().onRetry(e -> LoggerFactory.getLogger(getClass()).atWarn()
        .addKeyValue("event", "hotel_retry").addKeyValue("attempt", e.getNumberOfRetryAttempts()).log("Retrying Hotel request"));
        breaker.getEventPublisher().onStateTransition(e -> LoggerFactory.getLogger(getClass()).atWarn()
        .addKeyValue("event", "hotel_circuit_transition").addKeyValue("transition", e.getStateTransition()).log("Hotel circuit changed"));
    }

    @Override public List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end, String userToken) {
        return execute(() -> {
            RoomView[] rooms = client.get().uri(builder -> {
                var uri = builder.path("/api/rooms/recommend").queryParam("startDate", start).queryParam("endDate", end);
                if (hotelId != null) uri.queryParam("hotelId", hotelId);
                return uri.build();
            }).
            headers(h -> {
                h.setBearerAuth(userToken);
                h.set(TraceIds.HEADER, trace());
            }).
            retrieve().body(RoomView[].class);
            if (rooms == null) throw new RemoteFailure("Hotel returned an empty response", null);
            return Arrays.asList(rooms);
        },
        true);
    }
    @Override public void confirm(BookingView booking) {
        slot(booking, "confirm-availability", true);
    }
    @Override public void release(BookingView booking) {
        slot(booking, "release", false);
    }
    private void slot(BookingView booking, String action, boolean useBreaker) {
        execute(() -> {
            client.post().uri("/api/rooms/{id}/{action}", booking.roomId(), action)
            .headers(h -> {
                h.setBearerAuth(tokens.internal());
                h.set(TraceIds.HEADER, trace());
                h.set("X-Booking-Id", booking.id().toString());
            })
            .body(new SlotRequest(booking.id(), booking.requestId(), booking.startDate(), booking.endDate()))
            .retrieve().toBodilessEntity();
            return null;
        },
        useBreaker);
    }
    private <T> T execute(Supplier<T> call, boolean useBreaker) {
        Supplier<T> mapped = () -> {
            try {
                return call.get();
            }
            catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                if (status == 409) throw new DomainException(409, "ROOM_BUSY", "Номер занят или операция уже отменена");
                if (status == 404) throw new DomainException(404, "ROOM_NOT_FOUND", "Номер или отель не найден");
                if (status == 400) throw new DomainException(400, "HOTEL_VALIDATION", "Hotel Service отклонил параметры");
                throw new RemoteFailure("Hotel HTTP " + status, ex);
            } catch
            (RestClientException ex) {
                throw new RemoteFailure("Hotel transport failure", ex);
            }
        };
        Supplier<T> guarded = useBreaker ? CircuitBreaker.decorateSupplier(breaker, mapped) : mapped;
        return Retry.decorateSupplier(retry, guarded).get();
    }
    private String trace() {
        return MDC.get("traceId") == null ? TraceIds.normalize(null) : MDC.get("traceId");
    }
    public CircuitBreaker circuitBreaker() {
        return breaker;
    }
}
