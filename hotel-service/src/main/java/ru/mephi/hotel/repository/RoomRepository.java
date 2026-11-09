package ru.mephi.hotel.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.mephi.hotel.domain.Room;
import ru.mephi.hotel.domain.InventoryBooking.State;

public interface RoomRepository extends JpaRepository<Room, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(@Param("id") Long id);
    boolean existsByHotelId(Long hotelId);
    List<Room> findByHotelId(Long hotelId);

    @Query("""
            select r from Room r where r.available = true and (:hotelId is null or r.hotel.id = :hotelId)
            and not exists (select b from InventoryBooking b where b.room = r and b.state = :state
            and b.startDate < :endDate and b.endDate > :startDate)
            """)
    List<Room> findFree(@Param("hotelId") Long hotelId, @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate, @Param("state") State state);
}
