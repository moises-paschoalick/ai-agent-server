package ai.agent.api.v1.dto;

import ai.agent.common.Page;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** {"object":"list","data","first_id","last_id","has_more"} */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ListResponse<T>(String object, List<T> data, String firstId, String lastId, boolean hasMore) {

    public static <T> ListResponse<T> of(Page<T> page) {
        return new ListResponse<>("list", page.data(), page.firstId(), page.lastId(), page.hasMore());
    }
}
