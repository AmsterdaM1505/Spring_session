package ru.mephi.booking;

import com.sun.net.httpserver.HttpServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.mephi.booking.api.BookingDtos.BookingView;
import ru.mephi.booking.domain.Booking.Status;
import ru.mephi.booking.integration.*;
import ru.mephi.booking.security.TokenService;
import ru.mephi.shared.DomainException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class HotelHttpClientTest {
    HttpServer server;
    ExecutorService workers;
    HotelHttpClient client;
    AtomicInteger confirmations = new AtomicInteger();
    AtomicInteger releases = new AtomicInteger();
    volatile int failures;
    volatile long delay;
    volatile int failureStatus = 503;
    BookingView booking = new BookingView(UUID.randomUUID(), UUID.randomUUID(), 1L, LocalDate.of(2030,1,1), LocalDate.of(2030,1,3), Status.PENDING, Instant.now(), null, false);
    @BeforeEach void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        workers = Executors.newCachedThreadPool();
        server.setExecutor(workers);
        server.createContext("/api/rooms/1/confirm-availability", exchange -> {
            int attempt = confirmations.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            try {
                if (delay > 0) Thread.sleep(delay);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            try {
                exchange.sendResponseHeaders(attempt <= failures ? failureStatus : 204, -1);
            }
            finally {
                exchange.close();
            }
        });
        server.createContext("/api/rooms/1/release", exchange -> {
            releases.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(300)).build());
        factory.setReadTimeout(Duration.ofMillis(300));
        var tokens = mock(TokenService.class);
        when(tokens.internal()).thenReturn("internal-test");
        client = new HotelHttpClient(RestClient.builder().baseUrl("http://127.0.0.1:"+server.getAddress().getPort()).requestFactory(factory).build(), tokens, 3, 10);
    }
    @AfterEach void tearDown() {
        server.stop(0);
        workers.shutdownNow();
    }
    @Test void successfulCallIsNotRetried() {
        client.confirm(booking);
        assertThat(confirmations.get()).isEqualTo(1);
    }
    @Test void transient503IsRetriedUpToSuccess() {
        failures = 2;
        client.confirm(booking);
        assertThat(confirmations.get()).isEqualTo(3);
    }
    @Test void retriesAreBounded() {
        failures = 100;
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(RemoteFailure.class);
        assertThat(confirmations.get()).isEqualTo(3);
    }
    @Test void conflictIsNotRetried() {
        failures = 100;
        failureStatus = 409;
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(DomainException.class);
        assertThat(confirmations.get()).isEqualTo(1);
    }
    @Test void readTimeoutIsBoundedAndRetried() {
        delay = 1500;
        long started = System.nanoTime();
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(RemoteFailure.class);
        assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(5));
        assertThat(confirmations.get()).isEqualTo(3);
    }
    @Test void openCircuitDoesNotBlockCompensation() {
        failures = 100;
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(RuntimeException.class);
        assertThat(client.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = confirmations.get();
        assertThatThrownBy(() -> client.confirm(booking)).isInstanceOf(RuntimeException.class);
        assertThat(confirmations.get()).isEqualTo(before);
        client.release(booking);
        assertThat(releases.get()).isEqualTo(1);
    }
}
