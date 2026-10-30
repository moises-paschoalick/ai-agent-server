package ai.agent.common;

import ai.agent.errors.ValidationException;

/**
 * Query of a /v1 list: limit (1-100, default 20), order (desc default | asc) and the
 * after/before cursors (ids).
 */
public record ListQuery(int limit, boolean ascending, String after, String before) {

    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 100;

    public static ListQuery of(Integer limit, String order, String after, String before) {
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (effectiveLimit < 1 || effectiveLimit > MAX_LIMIT) {
            throw new ValidationException("limit must be between 1 and " + MAX_LIMIT, "limit");
        }
        boolean ascending;
        if (order == null || order.equals("desc")) {
            ascending = false;
        } else if (order.equals("asc")) {
            ascending = true;
        } else {
            throw new ValidationException("order must be 'asc' or 'desc'", "order");
        }
        return new ListQuery(effectiveLimit, ascending, blankToNull(after), blankToNull(before));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
