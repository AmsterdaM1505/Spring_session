package ru.mephi.shared;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DateWindowTest {
    private final LocalDate date = LocalDate.of(2030, 1, 1);
    @Test void adjacentStaysDoNotOverlap() {
        assertThat(new DateWindow(date, date.plusDays(2)).overlaps(new DateWindow(date.plusDays(2), date.plusDays(4)))).isFalse();
    }
    @Test void nestedStaysOverlapAndIntersectionIsClipped() {
        var outer = new DateWindow(date, date.plusDays(10));
        var inner = new DateWindow(date.plusDays(3), date.plusDays(5));
        assertThat(outer.overlaps(inner)).isTrue();
        assertThat(outer.intersectionNights(inner)).isEqualTo(2);
    }
    @Test void zeroOrNegativeDurationIsRejected() {
        assertThatThrownBy(() -> new DateWindow(date, date)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new DateWindow(date, date.minusDays(1))).isInstanceOf(DomainException.class);
    }
    @Test void tooLongStayIsRejected() {
        assertThatThrownBy(() -> new DateWindow(date, date.plusDays(366))).isInstanceOf(DomainException.class);
    }
    @Test void rankingUsesCounterThenId() {
        var sorted = List.of(new RoomView(3L, 1L, "3", true, 1), new RoomView(2L, 1L, "2", true, 0),
        new RoomView(1L, 1L, "1", true, 0)).stream().sorted(RoomRanking.recommendation()).map(RoomView::id).toList();
        assertThat(sorted).containsExactly(1L, 2L, 3L);
    }
    @Test void traceHeaderCannotInjectLogLines() {
        assertThat(TraceIds.normalize("hello\r\nforged=true")).matches("[0-9a-f-]{36}");
    }
}
