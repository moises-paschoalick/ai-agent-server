package ai.agent.common;

import java.security.SecureRandom;

/**
 * Contract ids: prefix + 24 alphanumeric characters (e.g. "thread_", "msg_", "run_").
 */
public final class Ids {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int LENGTH = 24;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String newId(String prefix) {
        StringBuilder id = new StringBuilder(prefix.length() + LENGTH).append(prefix);
        for (int i = 0; i < LENGTH; i++) {
            id.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
