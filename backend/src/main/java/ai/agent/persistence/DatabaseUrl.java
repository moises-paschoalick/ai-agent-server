package ai.agent.persistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * DATABASE_URL in the Railway/Heroku form ({@code postgresql://user:password@host:port/db?params})
 * or as a JDBC URL ({@code jdbc:postgresql://...}). {@link #masked()} is the only form that may be
 * logged or shown: the password never leaves this record.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password, String masked) {

    private static final Pattern JDBC_PASSWORD = Pattern.compile("(?i)(password=)[^&;]*");

    @Override
    public String toString() {
        return masked;
    }

    public static DatabaseUrl parse(String value) {
        String url = value == null ? "" : value.strip();
        if (url.isEmpty()) {
            throw new IllegalStateException("DATABASE_URL is required when AGENT_STORE=postgres.");
        }
        if (url.startsWith("jdbc:postgresql:")) {
            return new DatabaseUrl(url, null, null, JDBC_PASSWORD.matcher(url).replaceAll("$1***"));
        }
        if (!url.startsWith("postgresql://") && !url.startsWith("postgres://")) {
            throw new IllegalStateException(
                    "DATABASE_URL must start with postgresql://, postgres:// or jdbc:postgresql: (got " + scheme(url) + ").");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("DATABASE_URL is not a valid URL.");
        }
        if (uri.getHost() == null) {
            throw new IllegalStateException("DATABASE_URL has no host.");
        }
        String username = null;
        String password = null;
        if (uri.getRawUserInfo() != null) {
            String[] parts = uri.getRawUserInfo().split(":", 2);
            username = decode(parts[0]);
            password = parts.length > 1 ? decode(parts[1]) : null;
        }
        String hostPort = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String jdbcUrl = "jdbc:postgresql://" + hostPort + path + query;
        String user = username == null ? "" : username + (password == null ? "" : ":***") + "@";
        String masked = "postgresql://" + user + hostPort + path + JDBC_PASSWORD.matcher(query).replaceAll("$1***");
        return new DatabaseUrl(jdbcUrl, username, password, masked);
    }

    private static String scheme(String url) {
        int colon = url.indexOf(':');
        return colon > 0 ? "'" + url.substring(0, colon) + "'" : "no scheme";
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
