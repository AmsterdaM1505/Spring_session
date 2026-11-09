package ru.mephi.booking.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.mephi.booking.api.BookingDtos.*;
import ru.mephi.booking.domain.Booking.Status;
import ru.mephi.booking.integration.HotelPort;
import ru.mephi.shared.DomainException;

@Service
@RequiredArgsConstructor
public class BookingCoordinator {
    private final BookingTransactions transactions;
    private final HotelPort hotel;

    public Creation create(long userId, CreateBooking input, String userToken) {
        Creation created = transactions.pending(userId, input);
        if (!created.created()) return created;
        UUID id = created.booking().id();
        try (var scope = org.slf4j.MDC.putCloseable("bookingId", id.toString())) {
            List<Long> candidates = input.autoSelect()
            ? hotel.recommend(input.hotelId(), input.startDate(), input.endDate(), userToken)
            .stream().map(ru.mephi.shared.RoomView::id).toList()
            : List.of(input.roomId());
            if (candidates.isEmpty()) throw new DomainException(409, "NO_AVAILABLE_ROOMS", "Свободных номеров нет");
            for (long roomId : candidates) {
                if (!transactions.selectRoom(id, roomId)) return new Creation(transactions.get(id), true);
                BookingView booking = transactions.get(id);
                try {
                    hotel.confirm(booking);
                }
                catch (DomainException ex) {
                    // A definitive 409 did not acquire a slot. An uncertain timeout must never try another room.
                    if (input.autoSelect() && ex.status() == 409) continue;
                    throw ex;
                }
                if (!transactions.confirm(id)) compensate(transactions.get(id));
                return new Creation(transactions.get(id), true);
            }
            throw new DomainException(409, "ROOM_BUSY", "Рекомендованные номера уже заняты другими запросами");
        } catch
        (RuntimeException ex) {
            String code = ex instanceof DomainException domain ? domain.code() : "HOTEL_UNAVAILABLE";
            BookingView failed = transactions.failPending(id, code);
            if (failed.status() == Status.CONFIRMED) return new Creation(failed, true);
            compensate(failed);
            if (ex instanceof DomainException domain) throw domain;
            LoggerFactory.getLogger(getClass()).atWarn().addKeyValue("bookingId", id)
            .addKeyValue("event", "booking_failed").setCause(ex).log("Hotel request failed");
            throw new DomainException(503, "HOTEL_UNAVAILABLE", "Hotel Service временно недоступен; бронирование отменено");
        }
    }

    public BookingView cancel(UUID id, long userId) {
        compensate(transactions.cancelOwn(id, userId));
        return transactions.getOwn(id, userId);
    }

    public void compensate(BookingView booking) {
        if (booking.status() != Status.CANCELLED || !booking.compensationPending()) return;
        try {
            try {
                hotel.release(booking);
            }
            catch (DomainException ex) {
                if (ex.status() != 404) throw ex;
            }
            transactions.released(booking.id());
        } catch
        (RuntimeException ex) {
            // releaseRequired is an outbox flag committed before the remote call; a scheduled worker retries it.
            LoggerFactory.getLogger(getClass()).atWarn().addKeyValue("event", "compensation_deferred")
            .addKeyValue("bookingId", booking.id()).setCause(ex).log("Slot release will be retried");
        }
    }
}
