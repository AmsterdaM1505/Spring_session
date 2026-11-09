package ru.mephi.shared;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record SlotRequest(@NotNull UUID bookingId, @NotNull UUID requestId,
@NotNull LocalDate startDate, @NotNull LocalDate endDate) {
    public DateWindow window() {
        return new DateWindow(startDate, endDate);
    }
}
