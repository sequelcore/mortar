package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.StringComparison;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

final class PostgresStringPredicateTest {
    private final TableRef clients = new TableRef("clients", "c");
    private final ColumnRef<String> name = clients.column("name", "name", String.class);

    @Test
    void rendersCaseSensitiveContainsPredicate() {
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(name.contains("Rio", StringComparison.caseSensitive()))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c where c.name like ?");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("%Rio%");
    }

    @Test
    void rendersCaseInsensitiveStartsWithPredicateWithCollation() {
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(name.startsWith("rio", StringComparison.caseInsensitive("und-x-icu")))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo(
            "select c.* from clients c where c.name collate \"und-x-icu\" ilike ?"
        );
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("rio%");
    }

    @Test
    void rendersCaseInsensitiveEndsWithPredicate() {
        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .where(name.endsWith("rio", StringComparison.caseInsensitive()))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c where c.name ilike ?");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly("%rio");
    }
}
