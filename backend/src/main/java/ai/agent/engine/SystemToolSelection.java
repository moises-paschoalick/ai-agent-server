package ai.agent.engine;

import java.util.Collection;
import java.util.Set;

/** Which system tools an execution may offer and run: all of them, or only the named ones. */
public record SystemToolSelection(Set<String> names) {

    public SystemToolSelection {
        names = names == null ? null : Set.copyOf(names);
    }

    public static SystemToolSelection all() {
        return new SystemToolSelection(null);
    }

    public static SystemToolSelection only(Collection<String> names) {
        return new SystemToolSelection(Set.copyOf(names));
    }

    public boolean allows(String name) {
        return names == null || names.contains(name);
    }
}
