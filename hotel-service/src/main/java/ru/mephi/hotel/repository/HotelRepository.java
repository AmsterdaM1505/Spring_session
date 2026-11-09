package ru.mephi.hotel.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.hotel.domain.Hotel;
public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
