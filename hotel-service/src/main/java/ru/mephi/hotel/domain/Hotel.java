package ru.mephi.hotel.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(name = "hotels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 250) private String address;
    @Version private long version;
    public Hotel(String name, String address) {
        update(name, address);
    }
    public void update(String name, String address) {
        this.name = name.strip();
        this.address = address.strip();
    }
}
