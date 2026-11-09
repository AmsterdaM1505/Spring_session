package ru.mephi.booking.repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.mephi.booking.domain.Booking;
import ru.mephi.booking.domain.Booking.Status;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByUserIdAndRequestId(long userId, UUID requestId);
    Page<Booking> findByUserId(long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") UUID id);
    @Query("select b.id from Booking b where (b.status = :pending and b.createdAt < :cutoff) or (b.status = :cancelled and b.releaseRequired = true)")
    List<UUID> recoveryCandidates(@Param("pending") Status pending, @Param("cancelled") Status cancelled,
    @Param("cutoff") Instant cutoff, Pageable pageable);
}
