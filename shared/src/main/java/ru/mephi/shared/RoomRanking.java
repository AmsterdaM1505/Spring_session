package ru.mephi.shared;

import java.util.Comparator;

public final class RoomRanking {
    private RoomRanking() {
    }
    public static Comparator<RoomView> recommendation() {
        return Comparator.comparingLong(RoomView::timesBooked).thenComparingLong(RoomView::id);
    }
}
