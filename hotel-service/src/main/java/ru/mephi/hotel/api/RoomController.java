package ru.mephi.hotel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.hotel.api.HotelDtos.*;
import ru.mephi.hotel.service.*;
import ru.mephi.shared.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class RoomController {
    private final HotelCatalog catalog;
    private final InventoryService inventory;
    @GetMapping public List<RoomView> free(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
    @RequestParam(required = false) Long hotelId, @RequestParam(defaultValue = "id") String sort) {
        return catalog.free(hotelId, startDate, endDate, false, sort);
    }
    @GetMapping("/recommend") public List<RoomView> recommend(@RequestParam LocalDate startDate, @RequestParam LocalDate endDate,
    @RequestParam(required = false) Long hotelId) {
        return catalog.free(hotelId, startDate, endDate, true, "id");
    }
    @GetMapping("/{id}") public RoomView one(@PathVariable long id) {
        return catalog.room(id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public RoomView create(@RequestBody @Valid RoomInput input) {
        return catalog.createRoom(input);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoomView update(@PathVariable long id, @RequestBody @Valid RoomUpdate input) {
        return catalog.updateRoom(id, input);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id) {
        catalog.deleteRoom(id);
    }
    @PostMapping("/{id}/confirm-availability")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('INTERNAL')")
    public void confirm(@PathVariable long id, @RequestBody @Valid SlotRequest request) {
        inventory.confirm(id, request);
    }
    @PostMapping("/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('INTERNAL')")
    public void release(@PathVariable long id, @RequestBody @Valid SlotRequest request) {
        inventory.release(id, request);
    }
}
