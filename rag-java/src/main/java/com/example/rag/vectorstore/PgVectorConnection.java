package com.example.rag.vectorstore;

import com.example.rag.config.PostgresProperties;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * JDBC coordinates for the pgvector database.
 *
 * <p>Accepts the same configuration the Python service used: either a full connection string
 * ({@code PGVECTOR_CONNECTION}, including the SQLAlchemy style {@code postgresql+psycopg://} scheme)
 * or the individual {@code POSTGRES_*} values.
 */
public record PgVectorConnection(String jdbcUrl, String username, String password) {

    private static final String DEFAULT_PORT = "5432";

    public static PgVectorConnection resolve(String connection, PostgresProperties postgres) {
        if (hasText(connection)) {
            return fromConnectionString(connection, postgres);
        }
        return fromParts(postgres);
    }

    private static PgVectorConnection fromConnectionString(String connection, PostgresProperties postgres) {
        String normalized = connection.trim();
        if (normalized.startsWith("jdbc:")) {
            return new PgVectorConnection(normalized, postgres.user(), postgres.password());
        }

        int schemeEnd = normalized.indexOf("://");
        if (schemeEnd < 0) {
            throw new IllegalStateException("Unsupported pgvector connection string: " + connection);
        }
        URI uri = URI.create("postgresql" + normalized.substring(schemeEnd));

        String host = uri.getHost() != null ? uri.getHost() : "localhost";
        String port = uri.getPort() > 0 ? String.valueOf(uri.getPort()) : DEFAULT_PORT;
        String database = trimLeadingSlash(uri.getPath());
        if (!hasText(database)) {
            throw new IllegalStateException("pgvector connection string is missing a database name: " + connection);
        }

        String username = postgres.user();
        String password = postgres.password();
        String userInfo = uri.getUserInfo();
        if (hasText(userInfo)) {
            int separator = userInfo.indexOf(':');
            if (separator < 0) {
                username = decode(userInfo);
            } else {
                username = decode(userInfo.substring(0, separator));
                password = decode(userInfo.substring(separator + 1));
            }
        }

        String query = hasText(uri.getQuery()) ? "?" + uri.getQuery() : "";
        return new PgVectorConnection("jdbc:postgresql://" + host + ":" + port + "/" + database + query,
                username, password);
    }

    private static PgVectorConnection fromParts(PostgresProperties postgres) {
        require(postgres.host(), "POSTGRES_HOST");
        require(postgres.db(), "POSTGRES_DB");
        require(postgres.user(), "POSTGRES_USER");
        require(postgres.password(), "POSTGRES_PASSWORD");
        String port = hasText(postgres.port()) ? postgres.port() : DEFAULT_PORT;
        String jdbcUrl = "jdbc:postgresql://" + postgres.host() + ":" + port + "/" + postgres.db();
        return new PgVectorConnection(jdbcUrl, postgres.user(), postgres.password());
    }

    private static void require(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalStateException(
                    "Missing database configuration: set PGVECTOR_CONNECTION or " + name + ".");
        }
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String trimLeadingSlash(String path) {
        if (path == null) {
            return null;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
