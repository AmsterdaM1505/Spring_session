package ru.mephi.hotel.api;

import jakarta.validation.constraints.*;

public final class HotelDtos {
    private HotelDtos() {
    }
    public record HotelInput(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 250) String address) {
    }
    public record RoomInput(@NotNull @Positive Long hotelId, @NotBlank @Size(max = 30) String number, @NotNull Boolean available) {
    }
    public record RoomUpdate(@NotBlank @Size(max = 30) String number, @NotNull Boolean available) {
    }
    public record HotelView(Long id, String name, String address) {
    }
    public record RoomStatistics(Long roomId, String number, boolean available, long timesBooked,
    long occupiedNights, long periodNights, double occupancyPercent) {
    }
    public record HotelStatistics(Long hotelId, int roomCount, long occupiedNights, long capacityNights,
    double occupancyPercent, java.util.List<RoomStatistics> rooms) {
    }
}
