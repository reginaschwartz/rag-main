package com.example.rag.vectorstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.rag.config.PostgresProperties;
import org.junit.jupiter.api.Test;

class PgVectorConnectionTest {

    private static final PostgresProperties EMPTY = new PostgresProperties(null, null, null, null, null);

    @Test
    void prefersTheConnectionStringAndAcceptsTheSqlAlchemyScheme() {
        PgVectorConnection connection =
                PgVectorConnection.resolve("postgresql+psycopg://raguser:ragpass@postgres:5432/ragdb", EMPTY);

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:postgresql://postgres:5432/ragdb");
        assertThat(connection.username()).isEqualTo("raguser");
        assertThat(connection.password()).isEqualTo("ragpass");
    }

    @Test
    void keepsQueryParametersAndDefaultsThePort() {
        PgVectorConnection connection =
                PgVectorConnection.resolve("postgresql://user:secret@db.example.com/ragdb?sslmode=require", EMPTY);

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:postgresql://db.example.com:5432/ragdb?sslmode=require");
    }

    @Test
    void decodesEscapedCredentials() {
        PgVectorConnection connection =
                PgVectorConnection.resolve("postgresql://rag%40user:p%40ss@localhost:5432/ragdb", EMPTY);

        assertThat(connection.username()).isEqualTo("rag@user");
        assertThat(connection.password()).isEqualTo("p@ss");
    }

    @Test
    void passesThroughJdbcUrls() {
        PostgresProperties postgres = new PostgresProperties(null, null, null, "raguser", "ragpass");

        PgVectorConnection connection =
                PgVectorConnection.resolve("jdbc:postgresql://localhost:5432/ragdb", postgres);

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/ragdb");
        assertThat(connection.username()).isEqualTo("raguser");
    }

    @Test
    void fallsBackToIndividualSettings() {
        PostgresProperties postgres =
                new PostgresProperties("localhost", "5433", "ragdb", "raguser", "ragpass");

        PgVectorConnection connection = PgVectorConnection.resolve(" ", postgres);

        assertThat(connection.jdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5433/ragdb");
        assertThat(connection.username()).isEqualTo("raguser");
        assertThat(connection.password()).isEqualTo("ragpass");
    }

    @Test
    void reportsMissingConfiguration() {
        assertThatThrownBy(() -> PgVectorConnection.resolve(null, EMPTY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PGVECTOR_CONNECTION");
    }
}
