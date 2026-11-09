package ru.mephi.hotel.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotel.domain.InventoryBooking;
import ru.mephi.hotel.domain.InventoryBooking.State;
import ru.mephi.hotel.domain.Room;
import ru.mephi.hotel.repository.InventoryRepository;
import ru.mephi.hotel.repository.RoomRepository;
import ru.mephi.shared.DomainException;
import ru.mephi.shared.SlotRequest;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);
    private final RoomRepository rooms;
    private final InventoryRepository bookings;

    @Transactional
    public void confirm(long roomId, SlotRequest request) {
        request.window();
        Room room = lockedRoom(roomId);
        InventoryBooking previous = bookings.findById(request.bookingId()).orElse(null);
        if (previous != null) {
            verify(previous, roomId, request);
            if (previous.getState() == State.RELEASED) {
                throw new DomainException(409, "SLOT_RELEASED", "Операция уже отменена");
            }
            event("slot_confirm_replayed", request, roomId);
            return;
        }
        if (!room.isAvailable() || bookings.existsByRoomIdAndStateAndStartDateLessThanAndEndDateGreaterThan(
        roomId, State.HELD, request.endDate(), request.startDate())) {
            throw new DomainException(409, "ROOM_BUSY", "Номер недоступен на выбранные даты");
        }
        bookings.save(new InventoryBooking(room, request, State.HELD));
        room.recordAllocation();
        event("slot_confirmed", request, roomId);
    }

    @Transactional
    public void release(long roomId, SlotRequest request) {
        request.window();
        Room room = lockedRoom(roomId);
        InventoryBooking previous = bookings.findById(request.bookingId()).orElse(null);
        if (previous == null) {
            bookings.save(new InventoryBooking(room, request, State.RELEASED));
        } else
        {
            verify(previous, roomId, request);
            previous.release();
        }
        event("slot_released", request, roomId);
    }

    private Room lockedRoom(long roomId) {
        return rooms.lockById(roomId).orElseThrow(() -> new DomainException(404, "ROOM_NOT_FOUND", "Номер не найден"));
    }
    private void verify(InventoryBooking previous, long roomId, SlotRequest request) {
        if (!previous.matches(roomId, request)) {
            throw new DomainException(409, "IDEMPOTENCY_CONFLICT", "Идентификатор уже связан с другими параметрами");
        }
    }
    private void event(String event, SlotRequest request, long roomId) {
        log.atInfo().addKeyValue("event", event).addKeyValue("bookingId", request.bookingId())
        .addKeyValue("requestId", request.requestId()).addKeyValue("roomId", roomId).log("Inventory operation");
    }
}
