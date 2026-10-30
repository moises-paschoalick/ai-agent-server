package ai.agent.common;

import ai.agent.errors.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * One page of a /v1 list. {@code chronological} is oldest first; the query decides the order
 * and the after/before cursors (exclusive).
 */
public record Page<T>(List<T> data, String firstId, String lastId, boolean hasMore) {

    public static <T> Page<T> of(List<T> chronological, Function<T, String> id, ListQuery query) {
        List<T> ordered = new ArrayList<>(chronological);
        if (!query.ascending()) {
            Collections.reverse(ordered);
        }
        if (query.after() != null) {
            ordered = ordered.subList(indexOf(ordered, id, query.after(), "after") + 1, ordered.size());
        }
        if (query.before() != null) {
            ordered = ordered.subList(0, indexOf(ordered, id, query.before(), "before"));
        }
        boolean hasMore = ordered.size() > query.limit();
        List<T> data = List.copyOf(ordered.subList(0, Math.min(query.limit(), ordered.size())));
        return new Page<>(data,
                data.isEmpty() ? null : id.apply(data.getFirst()),
                data.isEmpty() ? null : id.apply(data.getLast()),
                hasMore);
    }

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(data.stream().map(mapper).toList(), firstId, lastId, hasMore);
    }

    private static <T> int indexOf(List<T> items, Function<T, String> id, String cursor, String param) {
        for (int i = 0; i < items.size(); i++) {
            if (cursor.equals(id.apply(items.get(i)))) {
                return i;
            }
        }
        throw new ValidationException("No item found with id '" + cursor + "'.", param);
    }
}
