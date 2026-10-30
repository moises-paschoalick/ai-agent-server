package ai.agent.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Who is calling, as the hash of the API key: set by the authentication filter for the request
 * thread and carried by the run worker to its own thread. The key itself is never kept.
 */
public final class AuditContext {

    private static final ThreadLocal<String> CALLER = new ThreadLocal<>();

    private AuditContext() {}

    /** First 12 hex characters of the SHA-256 of the key: tells clients apart, does not reveal the key. */
    public static String hash(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String caller() {
        return CALLER.get();
    }

    /** Runs {@code work} as {@code callerHash}, restoring the previous caller afterwards. */
    public static void runAs(String callerHash, Runnable work) {
        String previous = enter(callerHash);
        try {
            work.run();
        } finally {
            exit(previous);
        }
    }

    /** Sets the caller and returns the previous one, to be given back to {@link #exit} in a finally. */
    public static String enter(String callerHash) {
        String previous = CALLER.get();
        CALLER.set(callerHash);
        return previous;
    }

    public static void exit(String previous) {
        if (previous == null) {
            CALLER.remove();
        } else {
            CALLER.set(previous);
        }
    }
}
