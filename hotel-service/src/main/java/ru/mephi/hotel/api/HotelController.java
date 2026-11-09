package ru.mephi.hotel.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.mephi.hotel.api.HotelDtos.*;
import ru.mephi.hotel.service.HotelCatalog;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class HotelController {
    private final HotelCatalog catalog;
    @GetMapping public List<HotelView> all(@RequestParam(required = false) String query) {
        return catalog.hotels(query);
    }
    @GetMapping("/{id}") public HotelView one(@PathVariable long id) {
        return catalog.hotel(id);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public HotelView create(@RequestBody @Valid HotelInput input) {
        return catalog.createHotel(input);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelView update(@PathVariable long id, @RequestBody @Valid HotelInput input) {
        return catalog.updateHotel(id, input);
    }
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable long id) {
        catalog.deleteHotel(id);
    }
    @GetMapping("/{id}/occupancy")
    @PreAuthorize("hasRole('ADMIN')")
    public HotelStatistics statistics(@PathVariable long id, @RequestParam LocalDate startDate, @RequestParam LocalDate endDate) {
        return catalog.statistics(id, startDate, endDate);
    }
}
