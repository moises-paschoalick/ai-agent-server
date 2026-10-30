package ai.agent.tools.system;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.Set;

/**
 * National holidays of Brazil: the fixed ones (Lei 662/1949, 6.802/1980, 14.759/2023) and Good
 * Friday. Carnival and Corpus Christi are optional days off ("ponto facultativo") and are not here.
 */
final class BrazilHolidays {

    private static final Set<MonthDay> FIXED = Set.of(
            MonthDay.of(1, 1), MonthDay.of(4, 21), MonthDay.of(5, 1), MonthDay.of(9, 7), MonthDay.of(10, 12),
            MonthDay.of(11, 2), MonthDay.of(11, 15), MonthDay.of(11, 20), MonthDay.of(12, 25));

    private BrazilHolidays() {
    }

    static boolean isHoliday(LocalDate date) {
        return FIXED.contains(MonthDay.from(date)) || date.equals(easter(date.getYear()).minusDays(2));
    }

    /** Easter Sunday (Gregorian), by the Meeus/Jones/Butcher algorithm. */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
