package ru.mephi.hotel;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import ru.mephi.hotel.domain.*;
import ru.mephi.hotel.repository.*;
import ru.mephi.hotel.service.*;
import ru.mephi.shared.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "eureka.client.enabled=false", "app.seed=false"
})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
    @Autowired InventoryService service;
    @Autowired HotelCatalog catalog;
    @Autowired HotelRepository hotels;
    @Autowired RoomRepository rooms;
    @Autowired InventoryRepository inventory;
    @Autowired MockMvc mvc;
    long roomId;
    long hotelId;
    final LocalDate start = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(10);

    @BeforeEach void setUp() {
        inventory.deleteAll();
        rooms.deleteAll();
        hotels.deleteAll();
        var hotel = hotels.save(new Hotel("Test hotel", "Address"));
        hotelId = hotel.getId();
        roomId = rooms.save(new Room(hotel, "101", true)).getId();
    }
    SlotRequest request() {
        return new SlotRequest(UUID.randomUUID(), UUID.randomUUID(), start, start.plusDays(2));
    }

    @Test void confirmationReplayDoesNotIncrementCounter() {
        var request = request();
        service.confirm(roomId, request);
        service.confirm(roomId, request);
        assertThat(inventory.count()).isEqualTo(1);
        assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
    }
    @Test void cancellationBeforeDelayedConfirmationPreventsResurrection() {
        var request = request();
        service.release(roomId, request);
        service.release(roomId, request);
        assertThatThrownBy(() -> service.confirm(roomId, request)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(409));
        assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isZero();
    }
    @Test void adjacentDatesAreAllowed() {
        var first = request();
        service.confirm(roomId, first);
        service.confirm(roomId, new SlotRequest(UUID.randomUUID(), UUID.randomUUID(), first.endDate(), first.endDate().plusDays(1)));
        assertThat(inventory.count()).isEqualTo(2);
    }
    @Test void releaseMakesRoomAvailable() {
        var request = request();
        service.confirm(roomId, request);
        service.release(roomId, request);
        assertThat(catalog.free(hotelId, start, start.plusDays(2), true, "id")).hasSize(1);
    }
    @Test void payloadCannotChangeUnderSameBookingId() {
        var request = request();
        service.confirm(roomId, request);
        var changed = new SlotRequest(request.bookingId(), request.requestId(), start, start.plusDays(3));
        assertThatThrownBy(() -> service.confirm(roomId, changed)).isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }
    @Test void parallelBookingsAcquireExactlyOneSlot() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch ready = new CountDownLatch(12), startGate = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                startGate.await();
                try {
                    service.confirm(roomId, request());
                    return 204;
                }
                catch (DomainException ex) {
                    return ex.status();
                }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            startGate.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : futures) statuses.add(result.get(20, TimeUnit.SECONDS));
            assertThat(statuses.stream().filter(s -> s == 204).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(s -> s == 409).count()).isEqualTo(11);
            assertThat(inventory.count()).isEqualTo(1);
            assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
        } finally
        {
            startGate.countDown();
            pool.shutdownNow();
        }
    }
    @Test void concurrentReplayAcquiresSlotOnlyOnce() throws Exception {
        var request = request();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int i=0;i<24;i++) jobs.add(() -> {
                service.confirm(roomId, request);
                return null;
            });
            for (var result : pool.invokeAll(jobs)) result.get(20, TimeUnit.SECONDS);
            assertThat(rooms.findById(roomId).orElseThrow().getTimesBooked()).isEqualTo(1);
        } finally
        {
            pool.shutdownNow();
        }
    }
    @Test void recommendationsExcludeMaintenanceAndUseLoad() {
        var hotel = hotels.findById(hotelId).orElseThrow();
        long unused = rooms.save(new Room(hotel, "102", true)).getId();
        rooms.save(new Room(hotel, "103", false));
        var old = new SlotRequest(UUID.randomUUID(), UUID.randomUUID(), start.plusDays(5), start.plusDays(6));
        service.confirm(roomId, old);
        assertThat(catalog.free(hotelId, start, start.plusDays(2), true, "id")).extracting(RoomView::id).containsExactly(unused, roomId);
    }
    @Test void occupancyClipsReservationToRequestedPeriod() {
        service.confirm(roomId, request());
        var stats = catalog.statistics(hotelId, start.plusDays(1), start.plusDays(5));
        assertThat(stats.occupiedNights()).isEqualTo(1);
        assertThat(stats.occupancyPercent()).isEqualTo(25.0);
    }
    @Test void sequentialPlanningBalancesAllocationCounters() {
        var hotel = hotels.findById(hotelId).orElseThrow();
        rooms.save(new Room(hotel, "102", true));
        rooms.save(new Room(hotel, "103", true));
        for (int i=0; i<9; i++) {
            LocalDate day = start.plusDays(i*2L);
            long selected = catalog.free(hotelId, day, day.plusDays(1), true, "id").get(0).id();
            service.confirm(selected, new SlotRequest(UUID.randomUUID(), UUID.randomUUID(), day, day.plusDays(1)));
        }
        assertThat(rooms.findAll()).extracting(Room::getTimesBooked).containsOnly(3L);
    }
    @Test void maintenanceRoomCannotBeConfirmed() {
        var room = rooms.findById(roomId).orElseThrow();
        room.update("101", false);
        rooms.save(room);
        assertThatThrownBy(() -> service.confirm(roomId, request())).isInstanceOf(DomainException.class);
    }
    @Test void anonymousCannotListHotels() throws Exception {
        mvc.perform(get("/api/hotels")).andExpect(status().isUnauthorized());
    }
    @Test void userCannotCreateHotel() throws Exception {
        mvc.perform(post("/api/hotels").with(jwt().authorities(() -> "ROLE_USER"))
        .contentType("application/json").content("{\"name\":\"A\",\"address\":\"B\"}")).andExpect(status().isForbidden());
    }
    @Test void adminCanCreateAndUpdateHotel() throws Exception {
        mvc.perform(post("/api/hotels").with(jwt().authorities(() -> "ROLE_ADMIN"))
        .contentType("application/json").content("{\"name\":\"A\",\"address\":\"B\"}")).andExpect(status().isCreated());
        mvc.perform(put("/api/hotels/"+hotelId).with(jwt().authorities(() -> "ROLE_ADMIN"))
        .contentType("application/json").content("{\"name\":\"Updated\",\"address\":\"B\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Updated"));
    }
    @Test void malformedDatesReturn400() throws Exception {
        mvc.perform(get("/api/rooms").param("startDate", "bad").param("endDate", "bad")
        .with(jwt().authorities(() -> "ROLE_USER"))).andExpect(status().isBadRequest());
    }
    @Test void userTokenCannotInvokeInternalOperation() throws Exception {
        mvc.perform(post("/api/rooms/"+roomId+"/release").with(jwt().authorities(() -> "ROLE_USER"))
        .contentType("application/json").content("{}")).andExpect(status().isForbidden());
    }
    @Test void hotelWithRoomsCannotBeDeleted() throws Exception {
        mvc.perform(delete("/api/hotels/"+hotelId).with(jwt().authorities(() -> "ROLE_ADMIN"))).andExpect(status().isConflict());
    }
}
