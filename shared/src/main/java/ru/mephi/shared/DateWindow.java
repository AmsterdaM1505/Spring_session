package ru.mephi.shared;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A stay occupies [start, end); checkout and the next check-in may share a date. */
public record DateWindow(LocalDate start, LocalDate end) {
    public DateWindow {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new DomainException(400, "INVALID_DATES", "Дата выезда должна быть позже даты заезда");
        }
        if (ChronoUnit.DAYS.between(start, end) > 365) {
            throw new DomainException(400, "INVALID_DATES", "Период не должен превышать 365 суток");
        }
    }

    public boolean overlaps(DateWindow other) {
        return start.isBefore(other.end) && end.isAfter(other.start);
    }

    public long intersectionNights(DateWindow other) {
        LocalDate left = start.isAfter(other.start) ? start : other.start;
        LocalDate right = end.isBefore(other.end) ? end : other.end;
        return Math.max(0, ChronoUnit.DAYS.between(left, right));
    }
}
