package ru.mephi.booking.integration;

import java.time.LocalDate;
import java.util.List;
import ru.mephi.booking.api.BookingDtos.BookingView;
import ru.mephi.shared.RoomView;

public interface HotelPort {
    List<RoomView> recommend(Long hotelId, LocalDate start, LocalDate end, String userToken);
    void confirm(BookingView booking);
    void release(BookingView booking);
}
