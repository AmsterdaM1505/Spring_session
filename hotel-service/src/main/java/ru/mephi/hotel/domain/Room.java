package ru.mephi.hotel.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "rooms", uniqueConstraints = @UniqueConstraint(columnNames = {
    "hotel_id", "number"
}),
indexes = @Index(name = "idx_room_load", columnList = "times_booked,id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id") private Hotel hotel;
    @Column(nullable = false, length = 30) private String number;
    @Column(nullable = false) private boolean available;
    @Column(name = "times_booked", nullable = false) private long timesBooked;
    @Version private long version;
    public Room(Hotel hotel, String number, boolean available) {
        this.hotel = hotel;
        update(number, available);
    }
    public void update(String number, boolean available) {
        this.number = number.strip();
        this.available = available;
    }
    public void recordAllocation() {
        timesBooked++;
    }
}
