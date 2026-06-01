package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PostgresSpecificPredicateTest {
    private final TableRef clients = new TableRef("clients", "c");

    @Test
    void rendersArrayContainsAndOverlapsPredicates() {
        ColumnRef<String[]> tags = clients.column("tags", "tags", String[].class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(PostgresPredicates.arrayContains(tags, List.of("vip", "beta")))
                .where(PostgresPredicates.arrayOverlaps(tags, List.of("active")))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c where c.tags @> array[?, ?]::text[] and c.tags && array[?]::text[]"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("vip", "beta", "active");
        assertThat(rendered.metadata().columns()).containsExactly(tags);
    }

    @Test
    void rendersJsonbContainsPredicate() {
        ColumnRef<Object> profile = clients.column("profile", "profile", Object.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(PostgresPredicates.jsonbContains(profile, "{\"status\":\"active\"}"))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c where c.profile @> ?::jsonb");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("{\"status\":\"active\"}");
    }

    @Test
    void rendersFullTextWebSearchPredicate() {
        ColumnRef<String> bio = clients.column("bio", "bio", String.class);

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(PostgresPredicates.webSearch(bio, "english", "founder"))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c where to_tsvector('english', c.bio) @@ websearch_to_tsquery('english', ?)"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("founder");
    }

    @Test
    void rejectsInvalidPostgresSpecificPredicateInputs() {
        ColumnRef<String[]> tags = clients.column("tags", "tags", String[].class);
        ColumnRef<String> bio = clients.column("bio", "bio", String.class);

        assertThatThrownBy(() -> PostgresPredicates.arrayContains(tags, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("values cannot be empty");
        assertThatThrownBy(() -> PostgresPredicates.arrayContains(bio, List.of("founder")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("PostgreSQL array predicates require an array column: bio");
        assertThatThrownBy(() -> PostgresPredicates.jsonbContains(bio, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("json cannot be blank");
        assertThatThrownBy(() -> PostgresPredicates.webSearch(bio, "english;drop", "founder"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Invalid PostgreSQL text search configuration: english;drop");
    }
}
