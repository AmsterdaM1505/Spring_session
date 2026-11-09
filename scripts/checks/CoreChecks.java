import java.time.LocalDate;
import java.util.*;
import ru.mephi.shared.*;

/** Small executable check for the dependency-free scheduling core. Not a replacement for JUnit. */
public class CoreChecks {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        LocalDate base = LocalDate.of(2030, 1, 1);
        Random random = new Random(5301);
        for (int i = 0; i < 10_000; i++) {
            int a = random.nextInt(100), b = a + 1 + random.nextInt(20);
            int c = random.nextInt(100), d = c + 1 + random.nextInt(20);
            DateWindow first = new DateWindow(base.plusDays(a), base.plusDays(b));
            DateWindow second = new DateWindow(base.plusDays(c), base.plusDays(d));
            Set<LocalDate> occupied = new HashSet<>();
            for (int day = a; day < b; day++) occupied.add(base.plusDays(day));
            long common = 0;
            for (int day = c; day < d; day++) if (occupied.contains(base.plusDays(day))) common++;
            check(first.overlaps(second) == (common > 0), "Overlap differs from enumerated dates");
            check(first.intersectionNights(second) == common, "Wrong intersection length");
            check(first.overlaps(second) == second.overlaps(first), "Asymmetric overlap");
        }
        check(!new DateWindow(base, base.plusDays(1)).overlaps(new DateWindow(base.plusDays(1),base.plusDays(2))), "Adjacent stays conflict");
        for (int duration : new int[]{0,-1,366}) {
            try { new DateWindow(base, base.plusDays(duration)); throw new AssertionError("Invalid stay accepted"); }
            catch (DomainException expected) { check(expected.status()==400, "Wrong validation status"); }
        }
        var ranked = List.of(new RoomView(3L,1L,"3",true,2),new RoomView(2L,1L,"2",true,0),new RoomView(1L,1L,"1",true,0))
                .stream().sorted(RoomRanking.recommendation()).map(RoomView::id).toList();
        check(ranked.equals(List.of(1L,2L,3L)), "Wrong ranking");
        String trace = UUID.randomUUID().toString();
        check(TraceIds.normalize(trace).equals(trace), "Trace ID changed");
        check(TraceIds.normalize("bad\r\nentry").matches("[0-9a-f-]{36}"), "Unsafe trace ID");
        System.out.println("CORE_CHECKS_PASS assertions=" + assertions + " randomIntervalPairs=10000 seed=5301");
    }
}
