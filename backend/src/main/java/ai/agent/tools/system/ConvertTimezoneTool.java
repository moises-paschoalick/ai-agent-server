package ai.agent.tools.system;

import ai.agent.tools.ToolDefinition;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * convert_timezone: the same instant in another IANA time zone. Without an offset, the date time
 * is read in "from" (default: the server time zone); without a date time, it is now.
 */
public class ConvertTimezoneTool implements SystemTool {

    public static final String NAME = "convert_timezone";

    private static final ToolDefinition DEFINITION;

    static {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("datetime", Map.of("type", "string",
                "description", "ISO 8601 date time, e.g. 2026-10-27T15:00:00 or 2026-10-27T15:00:00-03:00 (empty = now)"));
        properties.put("from", Map.of("type", "string",
                "description", "IANA time zone of a date time without offset, e.g. America/Sao_Paulo (empty = server time zone)"));
        properties.put("to", Map.of("type", "string", "description", "IANA time zone to convert to, e.g. Europe/Lisbon"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("to"));
        DEFINITION = new ToolDefinition(NAME,
                "Converts a date and time (or now) to another IANA time zone. Returns ISO 8601 with offset.",
                parameters, false);
    }

    private final Clock clock;
    private final ZoneId zone;

    public ConvertTimezoneTool(Clock clock, ZoneId zone) {
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
        ZoneId to = zone(args.requiredText("to"));
        String from = args.text("from");
        ZoneId source = from == null ? zone : zone(from);
        ZonedDateTime instant = instant(args.text("datetime"), source);
        return instant.withZoneSameInstant(to).truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private ZonedDateTime instant(String text, ZoneId source) {
        if (text == null) {
            return ZonedDateTime.now(clock);
        }
        try {
            return OffsetDateTime.parse(text).toZonedDateTime();
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(text).atZone(source);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("datetime '" + text + "' must be ISO 8601, e.g. 2026-10-27T15:00:00");
            }
        }
    }

    private static ZoneId zone(String id) {
        try {
            return ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("unknown time zone '" + id + "' (use an IANA id such as America/Sao_Paulo)");
        }
    }
}
