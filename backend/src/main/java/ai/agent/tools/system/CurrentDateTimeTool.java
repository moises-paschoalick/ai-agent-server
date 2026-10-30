package ai.agent.tools.system;

import ai.agent.tools.ToolDefinition;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/** get_current_datetime: now, in ISO 8601 with offset, in the server time zone. */
public class CurrentDateTimeTool implements SystemTool {

    public static final String NAME = "get_current_datetime";

    private static final ToolDefinition DEFINITION = new ToolDefinition(NAME,
            "Returns the current date and time in ISO 8601 with offset, in the server time zone",
            ToolDefinition.emptyParameters(), false);

    private final Clock clock;
    private final ZoneId zone;

    public CurrentDateTimeTool(Clock clock, ZoneId zone) {
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public String execute(String argumentsJson) {
        return ZonedDateTime.now(clock).withZoneSameInstant(zone)
                .truncatedTo(ChronoUnit.SECONDS)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
