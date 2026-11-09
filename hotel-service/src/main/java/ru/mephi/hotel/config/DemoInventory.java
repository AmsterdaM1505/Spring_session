package ru.mephi.hotel.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.mephi.hotel.domain.*;
import ru.mephi.hotel.repository.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed", havingValue = "true")
public class DemoInventory implements ApplicationRunner {
    private final HotelRepository hotels;
    private final RoomRepository rooms;
    @Override
    @Transactional public void run(ApplicationArguments args) {
        if (hotels.count() != 0) return;
        Hotel campus = hotels.save(new Hotel("Кампус", "Москва, Каширское шоссе, 31"));
        Hotel center = hotels.save(new Hotel("Центральная", "Москва, ул. Садовая, 10"));
        rooms.save(new Room(campus, "101", true));
        rooms.save(new Room(campus, "102", true));
        rooms.save(new Room(campus, "103", true));
        rooms.save(new Room(campus, "104", false));
        rooms.save(new Room(center, "201", true));
        rooms.save(new Room(center, "202", true));
    }
}
