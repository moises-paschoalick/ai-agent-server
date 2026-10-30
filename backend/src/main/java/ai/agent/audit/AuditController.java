package ai.agent.audit;

import ai.agent.errors.ValidationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/** GET /audit/events: the audit trail, newest first, with optional filters. */
@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditLog audit;

    public AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping("/events")
    public List<AuditEvent> events(@RequestParam(name = "thread_id", required = false) String threadId,
                                   @RequestParam(name = "run_id", required = false) String runId,
                                   @RequestParam(name = "assistant_id", required = false) String assistantId,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String since,
                                   @RequestParam(required = false) String limit) {
        return audit.query(new AuditQuery(blankToNull(threadId), blankToNull(runId), blankToNull(assistantId),
                blankToNull(type), parseSince(since), parseLimit(limit)));
    }

    private static Instant parseSince(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw new ValidationException("since must be an ISO-8601 instant, like 2026-11-01T12:00:00Z", "since");
        }
    }

    private static int parseLimit(String value) {
        if (value == null || value.isBlank()) {
            return AuditQuery.DEFAULT_LIMIT;
        }
        try {
            int limit = Integer.parseInt(value.strip());
            if (limit >= 1 && limit <= AuditQuery.MAX_LIMIT) {
                return limit;
            }
        } catch (NumberFormatException e) {
            // falls through to the same message
        }
        throw new ValidationException("limit must be between 1 and " + AuditQuery.MAX_LIMIT, "limit");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
