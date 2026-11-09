package ru.mephi.booking.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import ru.mephi.booking.api.BookingDtos.*;
import ru.mephi.booking.domain.Booking.Status;
import ru.mephi.booking.service.*;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class BookingController {
    private final BookingCoordinator coordinator;
    private final BookingTransactions transactions;
    private final AccountService accounts;
    @PostMapping public ResponseEntity<BookingView> create(@RequestBody @Valid CreateBooking input, @AuthenticationPrincipal Jwt jwt) {
        long userId = current(jwt);
        Creation result = coordinator.create(userId, input, jwt.getTokenValue());
        BookingView view = result.booking();
        int status = view.status() == Status.PENDING ? 202 : result.created() ? 201 : 200;
        return ResponseEntity.status(status).location(URI.create("/api/bookings/" + view.id())).body(view);
    }
    @GetMapping public History history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
    @AuthenticationPrincipal Jwt jwt) {
        return transactions.history(current(jwt), page, size);
    }
    @GetMapping("/{id}") public BookingView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return transactions.getOwn(id, current(jwt));
    }
    @DeleteMapping("/{id}") public BookingView cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return coordinator.cancel(id, current(jwt));
    }
    private long current(Jwt jwt) {
        long id = Long.parseLong(jwt.getSubject());
        accounts.current(id);
        return id;
    }
}
