package ru.mephi.hotel.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.hotel.domain.InventoryBooking;
import ru.mephi.hotel.domain.InventoryBooking.State;

public interface InventoryRepository extends JpaRepository<InventoryBooking, UUID> {
    boolean existsByRoomIdAndStateAndStartDateLessThanAndEndDateGreaterThan(Long roomId, State state, LocalDate endDate, LocalDate startDate);
    List<InventoryBooking> findByRoomIdAndStateAndStartDateLessThanAndEndDateGreaterThan(Long roomId, State state, LocalDate endDate, LocalDate startDate);
    boolean existsByRoomId(Long roomId);
}
