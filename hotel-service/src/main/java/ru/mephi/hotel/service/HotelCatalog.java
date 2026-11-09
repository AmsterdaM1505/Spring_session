package ru.mephi.hotel.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotel.api.HotelDtos.*;
import ru.mephi.hotel.domain.Hotel;
import ru.mephi.hotel.domain.Room;
import ru.mephi.hotel.domain.InventoryBooking.State;
import ru.mephi.hotel.repository.*;
import ru.mephi.shared.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotelCatalog {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    private final InventoryRepository inventory;

    public List<HotelView> hotels(String query) {
        String filter = query == null ? "" : query.toLowerCase(java.util.Locale.ROOT);
        return hotels.findAll(org.springframework.data.domain.Sort.by("id")).stream()
        .filter(h -> (h.getName() + " " + h.getAddress()).toLowerCase(java.util.Locale.ROOT).contains(filter))
        .map(this::view).toList();
    }
    public HotelView hotel(long id) {
        return view(requireHotel(id));
    }

    @Transactional public HotelView createHotel(HotelInput input) {
        return view(hotels.save(new Hotel(input.name(), input.address())));
    }
    @Transactional public HotelView updateHotel(long id, HotelInput input) {
        Hotel hotel = requireHotel(id);
        hotel.update(input.name(), input.address());
        return view(hotel);
    }
    @Transactional public void deleteHotel(long id) {
        Hotel hotel = requireHotel(id);
        if (rooms.existsByHotelId(id)) throw new DomainException(409, "HOTEL_HAS_ROOMS", "Сначала удалите номера отеля");
        hotels.delete(hotel);
    }
    @Transactional public RoomView createRoom(RoomInput input) {
        return view(rooms.save(new Room(requireHotel(input.hotelId()), input.number(), input.available())));
    }
    @Transactional public RoomView updateRoom(long id, RoomUpdate input) {
        Room room = rooms.lockById(id).orElseThrow(this::roomMissing);
        room.update(input.number(), input.available());
        return view(room);
    }
    @Transactional public void deleteRoom(long id) {
        Room room = rooms.lockById(id).orElseThrow(this::roomMissing);
        if (inventory.existsByRoomId(id)) throw new DomainException(409, "ROOM_HAS_HISTORY", "Номер с историей следует вывести из эксплуатации");
        rooms.delete(room);
    }
    public RoomView room(long id) {
        return view(rooms.findById(id).orElseThrow(this::roomMissing));
    }

    public List<RoomView> free(Long hotelId, LocalDate start, LocalDate end, boolean recommend, String sort) {
        new DateWindow(start, end);
        if (start.isBefore(LocalDate.now(java.time.ZoneOffset.UTC))) throw new DomainException(400, "PAST_DATE", "Дата заезда уже прошла");
        if (hotelId != null) requireHotel(hotelId);
        var result = rooms.findFree(hotelId, start, end, State.HELD).stream().map(this::view);
        if (recommend) return result.sorted(RoomRanking.recommendation()).toList();
        Comparator<RoomView> order = switch (sort) {
            case "id" -> Comparator.comparingLong(RoomView::id);
            case "number" -> Comparator.comparing(RoomView::number).thenComparingLong(RoomView::id);
            default -> throw new DomainException(400, "INVALID_SORT", "Допустимы сортировки id и number");
        };
        return result.sorted(order).toList();
    }

    public HotelStatistics statistics(long hotelId, LocalDate start, LocalDate end) {
        requireHotel(hotelId);
        DateWindow window = new DateWindow(start, end);
        long nights = ChronoUnit.DAYS.between(start, end);
        List<RoomStatistics> detail = rooms.findByHotelId(hotelId).stream().sorted(Comparator.comparing(Room::getId)).map(room -> {
            long occupied = inventory.findByRoomIdAndStateAndStartDateLessThanAndEndDateGreaterThan(room.getId(), State.HELD, end, start)
            .stream().mapToLong(b -> window.intersectionNights(new DateWindow(b.getStartDate(), b.getEndDate()))).sum();
            return new RoomStatistics(room.getId(), room.getNumber(), room.isAvailable(), room.getTimesBooked(), occupied, nights, percent(occupied, nights));
        }).
        toList();
        long occupied = detail.stream().mapToLong(RoomStatistics::occupiedNights).sum();
        long capacity = nights * detail.size();
        return new HotelStatistics(hotelId, detail.size(), occupied, capacity, percent(occupied, capacity), detail);
    }
    private double percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(10000.0 * part / whole) / 100.0;
    }
    private Hotel requireHotel(long id) {
        return hotels.findById(id).orElseThrow(() -> new DomainException(404, "HOTEL_NOT_FOUND", "Отель не найден"));
    }
    private DomainException roomMissing() {
        return new DomainException(404, "ROOM_NOT_FOUND", "Номер не найден");
    }
    private HotelView view(Hotel h) {
        return new HotelView(h.getId(), h.getName(), h.getAddress());
    }
    private RoomView view(Room r) {
        return new RoomView(r.getId(), r.getHotel().getId(), r.getNumber(), r.isAvailable(), r.getTimesBooked());
    }
}
