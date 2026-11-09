package ru.mephi.booking.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import ru.mephi.booking.domain.Booking;

public final class BookingDtos {
    private BookingDtos() {
    }
    public record CreateBooking(@NotNull UUID requestId, @Positive Long roomId, @Positive Long hotelId,
    boolean autoSelect, @NotNull @FutureOrPresent LocalDate startDate,
    @NotNull LocalDate endDate) {
        public String fingerprint() {
            return (autoSelect ? "auto:" + hotelId : "room:" + roomId) + ":" + startDate + ":" + endDate;
        }
    }
    public record BookingView(UUID id, UUID requestId, Long roomId, LocalDate startDate, LocalDate endDate,
    Booking.Status status, Instant createdAt, String failureCode, boolean compensationPending) {
        public static BookingView from(Booking b) {
            return new BookingView(b.getId(), b.getRequestId(), b.getRoomId(), b.getStartDate(), b.getEndDate(),
            b.getStatus(), b.getCreatedAt(), b.getFailureCode(), b.isReleaseRequired());
        }
    }
    public record History(List<BookingView> content, int page, int size, long totalElements, int totalPages) {
    }
    public record Creation(BookingView booking, boolean created) {
    }
}
