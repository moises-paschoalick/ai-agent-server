package ai.agent.tools.system;

import ai.agent.tools.ToolDefinition;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * date_calc: date arithmetic the model often gets wrong. "add" moves a date by days, weeks,
 * months, years or business days; "diff" counts them between two dates. Business days skip
 * weekends and, with holidays "BR" (default), the national holidays of Brazil.
 */
public class DateCalcTool implements SystemTool {

    public static final String NAME = "date_calc";

    static final long MAX_BUSINESS_DAYS = 3650;

    private static final ToolDefinition DEFINITION;

    static {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", Map.of("type", "string", "enum", List.of("add", "diff"),
                "description", "add: date + amount units; diff: units from start to end"));
        properties.put("date", Map.of("type", "string", "description", "add: start date YYYY-MM-DD (empty = today)"));
        properties.put("amount", Map.of("type", "integer", "description", "add: how many units (negative goes back)"));
        properties.put("start", Map.of("type", "string", "description", "diff: first date YYYY-MM-DD (empty = today)"));
        properties.put("end", Map.of("type", "string", "description", "diff: last date YYYY-MM-DD (empty = today)"));
        properties.put("unit", Map.of("type", "string",
                "enum", List.of("days", "weeks", "months", "years", "business_days"),
                "description", "years only for add"));
        properties.put("holidays", Map.of("type", "string", "enum", List.of("BR", "none"),
                "description", "national holidays of Brazil count as non-business days (default BR)"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("operation", "unit"));
        DEFINITION = new ToolDefinition(NAME,
                "Adds to a date or counts the time between two dates, including business days with Brazilian national holidays. "
                        + "Returns {\"result\", \"weekday\"}. Example: {\"operation\":\"add\",\"date\":\"2026-12-18\",\"amount\":5,\"unit\":\"business_days\"}",
                parameters, false);
    }

    private final Clock clock;
    private final ZoneId zone;

    public DateCalcTool(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public String execute(String argumentsJson) {
        SystemToolArguments args = SystemToolArguments.parse(argumentsJson);
        String operation = args.requiredText("operation");
        String unit = args.requiredText("unit");
        boolean holidays = holidays(args.text("holidays"));
        Map<String, Object> result = new LinkedHashMap<>();
        switch (operation) {
            case "add" -> {
                LocalDate date = add(date(args, "date"), args.requiredLong("amount"), unit, holidays);
                result.put("result", date.toString());
                result.put("weekday", date.getDayOfWeek().name().toLowerCase(Locale.ROOT));
            }
            case "diff" -> {
                result.put("result", diff(date(args, "start"), date(args, "end"), unit, holidays));
                result.put("weekday", null);
            }
            default -> throw new IllegalArgumentException("operation must be 'add' or 'diff'");
        }
        return SystemToolArguments.toJson(result);
    }

    private LocalDate date(SystemToolArguments args, String field) {
        String text = args.text(field);
        if (text == null) {
            return LocalDate.now(clock.withZone(zone));
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " '" + text + "' must use the format YYYY-MM-DD");
        }
    }

    private static boolean holidays(String value) {
        if (value == null || value.equalsIgnoreCase("BR")) {
            return true;
        }
        if (value.equalsIgnoreCase("none")) {
            return false;
        }
        throw new IllegalArgumentException("holidays must be 'BR' or 'none'");
    }

    static LocalDate add(LocalDate date, long amount, String unit, boolean holidays) {
        return switch (unit) {
            case "days" -> date.plusDays(amount);
            case "weeks" -> date.plusWeeks(amount);
            case "months" -> date.plusMonths(amount);
            case "years" -> date.plusYears(amount);
            case "business_days" -> {
                if (Math.abs(amount) > MAX_BUSINESS_DAYS) {
                    throw new IllegalArgumentException("amount of business_days must be at most " + MAX_BUSINESS_DAYS);
                }
                long step = amount < 0 ? -1 : 1;
                LocalDate current = date;
                for (long left = Math.abs(amount); left > 0; ) {
                    current = current.plusDays(step);
                    if (isBusinessDay(current, holidays)) {
                        left--;
                    }
                }
                yield current;
            }
            default -> throw new IllegalArgumentException("unit must be days, weeks, months, years or business_days");
        };
    }

    /** Units from start (exclusive) to end (inclusive); negative when end comes first. */
    static long diff(LocalDate start, LocalDate end, String unit, boolean holidays) {
        return switch (unit) {
            case "days" -> ChronoUnit.DAYS.between(start, end);
            case "weeks" -> ChronoUnit.WEEKS.between(start, end);
            case "months" -> ChronoUnit.MONTHS.between(start, end);
            case "business_days" -> {
                boolean backwards = end.isBefore(start);
                LocalDate from = backwards ? end : start;
                LocalDate to = backwards ? start : end;
                if (ChronoUnit.DAYS.between(from, to) > MAX_BUSINESS_DAYS * 2) {
                    throw new IllegalArgumentException("dates are too far apart for business_days");
                }
                long count = 0;
                for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
                    if (isBusinessDay(d, holidays)) {
                        count++;
                    }
                }
                yield backwards ? -count : count;
            }
            default -> throw new IllegalArgumentException("unit for diff must be days, weeks, months or business_days");
        };
    }

    static boolean isBusinessDay(LocalDate date, boolean holidays) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY && !(holidays && BrazilHolidays.isHoliday(date));
    }
}
