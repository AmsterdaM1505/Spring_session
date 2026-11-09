package ru.mephi.booking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "bookings", uniqueConstraints = @UniqueConstraint(columnNames = {
    "user_id", "request_id"
}),
indexes = {
    @Index(name = "idx_booking_owner_date", columnList = "user_id,created_at"),
    @Index(name = "idx_booking_recovery", columnList = "status,release_required,created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {
    public enum Status {
        PENDING, CONFIRMED, CANCELLED
    }
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id") private AppUser user;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(nullable = false, length = 200) private String fingerprint;
    @Column(name = "room_id") private Long roomId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16) private Status status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Column(name = "release_required", nullable = false) private boolean releaseRequired;
    @Column(length = 64) private String failureCode;
    @Version private long version;

    public Booking(AppUser user, UUID requestId, String fingerprint, LocalDate start, LocalDate end) {
        id = UUID.randomUUID();
        this.user = user;
        this.requestId = requestId;
        this.fingerprint = fingerprint;
        startDate = start;
        endDate = end;
        status = Status.PENDING;
        createdAt = Instant.now();
        updatedAt = createdAt;
    }
    public boolean selectRoom(long roomId) {
        if (status != Status.PENDING) return false;
        this.roomId = roomId;
        updatedAt = Instant.now();
        return true;
    }
    public boolean confirm() {
        if (status != Status.PENDING) return false;
        status = Status.CONFIRMED;
        updatedAt = Instant.now();
        return true;
    }
    public void cancel(String reason) {
        if (status == Status.CANCELLED) return;
        status = Status.CANCELLED;
        failureCode = reason;
        releaseRequired = roomId != null;
        updatedAt = Instant.now();
    }
    public void released() {
        releaseRequired = false;
        updatedAt = Instant.now();
    }
}
