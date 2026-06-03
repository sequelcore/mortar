package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class ScalarSpecTest {
    @Test
    void countSpecCapturesTablePredicatesAndMetadata() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);

        CountSpec count = new SimpleMortarDb()
            .from(clients)
            .where(id.eq(7L))
            .where(active.eq(true))
            .count();

        assertThat(count.scalarType()).isEqualTo(Long.class);
        assertThat(count.table()).isEqualTo(clients);
        assertThat(count.predicates()).containsExactly(id.eq(7L), active.eq(true));
        assertThat(QueryMetadata.from(count).tables()).containsExactly(clients);
        assertThat(QueryMetadata.from(count).columns()).containsExactly(id, active);
    }

    @Test
    void existsSpecIsInspectableAsBooleanScalar() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);

        MortarBoundScalar<Boolean> scalar = new SimpleMortarDb()
            .from(clients)
            .where(id.eq(7L))
            .where(active.eq(true))
            .exists(renderer("select exists (select 1 from clients c where c.id = ? and c.active = ?)"))
            .named("ClientRepository.existsActive");

        assertThat(scalar.queryName()).contains("ClientRepository.existsActive");
        assertThat(scalar.scalarType()).isEqualTo(Boolean.class);
        assertThat(scalar.sql()).isEqualTo("select exists (select 1 from clients c where c.id = ? and c.active = ?)");
        assertThat(scalar.parameters()).containsExactly(Parameter.of(7L), Parameter.of(true));
        assertThat(scalar.parameterTypes()).containsExactly(Long.class, Boolean.class);
        assertThat(scalar.metadata().columns()).containsExactly(id, active);
    }

    @Test
    void scalarTerminalRejectsRowQueryOnlyShapeModifiers() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);

        assertThatThrownBy(() -> new SimpleMortarDb().from(clients).select(id).count())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("scalar queries cannot select columns, project rows, sort, limit, or offset");
        assertThatThrownBy(() -> new SimpleMortarDb().from(clients).orderBy(id.asc()).exists())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("scalar queries cannot select columns, project rows, sort, limit, or offset");
    }

    private QueryRenderer renderer(String sql) {
        return new QueryRenderer() {
            @Override
            public RenderedQuery render(QuerySpec query) {
                throw new AssertionError("query renderer should not render row queries");
            }

            @Override
            public RenderedQuery render(ExistsSpec exists) {
                return new RenderedQuery(sql, List.of(Parameter.of(7L), Parameter.of(true)), QueryMetadata.from(exists));
            }
        };
    }
}
