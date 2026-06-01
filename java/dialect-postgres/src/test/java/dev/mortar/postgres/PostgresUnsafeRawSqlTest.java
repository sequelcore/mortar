package dev.mortar.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

final class PostgresUnsafeRawSqlTest {
    @Test
    void rendersExplicitUnsafeRawPredicate() {
        TableRef clients = new TableRef("clients", "c");

        RenderedQuery rendered = new PostgresQueryRenderer().render(
            new SimpleMortarDb()
                .from(clients)
                .unsafeWhereRaw("c.score > ?", Parameter.of(10))
                .build()
        );

        assertThat(rendered.sql()).isEqualTo("select c.* from clients c where c.score > ?");
        assertThat(rendered.parameters())
            .extracting(parameter -> parameter.value())
            .containsExactly(10);
    }
}
