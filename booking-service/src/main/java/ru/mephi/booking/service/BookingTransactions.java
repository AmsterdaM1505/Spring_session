package ru.mephi.booking.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.booking.api.BookingDtos.*;
import ru.mephi.booking.domain.Booking;
import ru.mephi.booking.domain.Booking.Status;
import ru.mephi.booking.repository.*;
import ru.mephi.shared.DateWindow;
import ru.mephi.shared.DomainException;

/** Each public method is one local transaction. Network calls belong to the coordinator. */
@Service
@RequiredArgsConstructor
public class BookingTransactions {
    private final BookingRepository bookings;
    private final UserRepository users;

    @Transactional public Creation pending(long userId, CreateBooking input) {
        new DateWindow(input.startDate(), input.endDate());
        if (!input.autoSelect() && input.roomId() == null) {
            throw new DomainException(400, "ROOM_REQUIRED", "Укажите roomId или включите autoSelect");
        }
        var user = users.lockById(userId).filter(ru.mephi.booking.domain.AppUser::isActive)
        .orElseThrow(() -> new DomainException(401, "ACCOUNT_INACTIVE", "Учётная запись недоступна"));
        var previous = bookings.findByUserIdAndRequestId(userId, input.requestId());
        if (previous.isPresent()) {
            if (!previous.get().getFingerprint().equals(input.fingerprint())) {
                throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "requestId уже использован с другими параметрами");
            }
            return new Creation(BookingView.from(previous.get()), false);
        }
        Booking booking = bookings.saveAndFlush(new Booking(user, input.requestId(), input.fingerprint(), input.startDate(), input.endDate()));
        event("booking_pending", booking);
        return new Creation(BookingView.from(booking), true);
    }

    @Transactional public boolean selectRoom(UUID id, long roomId) {
        return locked(id).selectRoom(roomId);
    }
    @Transactional public boolean confirm(UUID id) {
        Booking booking = locked(id);
        boolean changed = booking.confirm();
        if (changed) event("booking_confirmed", booking);
        return changed;
    }
    @Transactional public BookingView failPending(UUID id, String reason) {
        Booking booking = locked(id);
        if (booking.getStatus() == Status.PENDING) {
            booking.cancel(reason);
            event("booking_cancelled", booking);
        }
        return BookingView.from(booking);
    }
    @Transactional public BookingView cancelOwn(UUID id, long userId) {
        Booking booking = locked(id);
        own(booking, userId);
        booking.cancel("USER_CANCELLED");
        event("booking_cancelled", booking);
        return BookingView.from(booking);
    }
    @Transactional public void released(UUID id) {
        Booking booking = locked(id);
        if (booking.getStatus() != Status.CANCELLED) throw new IllegalStateException("Only cancelled bookings can be released");
        booking.released();
        event("compensation_completed", booking);
    }
    @Transactional(readOnly = true) public BookingView get(UUID id) {
        return BookingView.from(require(id));
    }
    @Transactional(readOnly = true) public BookingView getOwn(UUID id, long userId) {
        Booking booking = require(id);
        own(booking, userId);
        return BookingView.from(booking);
    }
    @Transactional(readOnly = true) public History history(long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new DomainException(400, "INVALID_PAGE", "page >= 0, size от 1 до 100");
        var result = bookings.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new History(result.map(BookingView::from).getContent(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true) public List<UUID> recoveryCandidates(Instant cutoff) {
        return bookings.recoveryCandidates(Status.PENDING, Status.CANCELLED, cutoff, PageRequest.of(0, 100, Sort.by("createdAt")));
    }
    private Booking locked(UUID id) {
        return bookings.lockById(id).orElseThrow(this::missing);
    }
    private Booking require(UUID id) {
        return bookings.findById(id).orElseThrow(this::missing);
    }
    private DomainException missing() {
        return new DomainException(404, "BOOKING_NOT_FOUND", "Бронирование не найдено");
    }
    private void own(Booking booking, long userId) {
        if (booking.getUser().getId() != userId) throw missing();
    }
    private void event(String event, Booking b) {
        LoggerFactory.getLogger(getClass()).atInfo().addKeyValue("event", event).addKeyValue("bookingId", b.getId())
        .addKeyValue("requestId", b.getRequestId()).addKeyValue("status", b.getStatus())
        .addKeyValue("roomId", b.getRoomId()).log("Booking transition");
    }
}
