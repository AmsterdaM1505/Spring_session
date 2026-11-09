package ru.mephi.hotel.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import ru.mephi.shared.SlotRequest;

/** Local occupancy projection. RELEASED rows also act as cancellation tombstones. */
@Entity
@Table(name = "bookings", indexes = @Index(name = "idx_slot_overlap", columnList = "room_id,state,start_date,end_date"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryBooking {
    public enum State {
        HELD, RELEASED
    }
    @Id
    @Column(name = "booking_id") private UUID bookingId;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id") private Room room;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private State state;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;

    public InventoryBooking(Room room, SlotRequest request, State state) {
        this.bookingId = request.bookingId();
        this.requestId = request.requestId();
        this.room = room;
        this.startDate = request.startDate();
        this.endDate = request.endDate();
        this.state = state;
        updatedAt = Instant.now();
    }
    public boolean matches(long roomId, SlotRequest request) {
        return room.getId() == roomId && requestId.equals(request.requestId())
        && startDate.equals(request.startDate()) && endDate.equals(request.endDate());
    }
    public void release() {
        state = State.RELEASED;
        updatedAt = Instant.now();
    }
}
