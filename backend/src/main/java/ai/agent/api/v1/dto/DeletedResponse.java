package ai.agent.api.v1.dto;

/** {"id","object":"<kind>.deleted","deleted":true} */
public record DeletedResponse(String id, String object, boolean deleted) {

    public static DeletedResponse of(String id, String kind) {
        return new DeletedResponse(id, kind + ".deleted", true);
    }
}
