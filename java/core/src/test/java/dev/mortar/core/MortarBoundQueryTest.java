package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class MortarBoundQueryTest {
    @Test
    void exposesRenderedSqlParametersTypesNameMetadataAndRowType() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        RenderedQuery renderedQuery = new RenderedQuery(
            "select c.id from clients c where c.id = ?",
            List.of(Parameter.of(7L)),
            new QueryMetadata(List.of(clients), List.of(id), List.of())
        );

        MortarBoundQuery<ClientRow> query = MortarBoundQuery.of(
            "ClientRepository.findById",
            renderedQuery,
            ClientRow.class
        );

        assertThat(query.queryName()).contains("ClientRepository.findById");
        assertThat(query.sql()).isEqualTo("select c.id from clients c where c.id = ?");
        assertThat(query.rendered()).isEqualTo(renderedQuery);
        assertThat(query.parameters()).containsExactly(Parameter.of(7L));
        assertThat(query.parameterTypes()).containsExactly(Long.class);
        assertThat(query.metadata().tables()).containsExactly(clients);
        assertThat(query.metadata().columns()).containsExactly(id);
        assertThat(query.rowType()).isEqualTo(ClientRow.class);
    }

    @Test
    void rejectsBlankQueryName() {
        RenderedQuery renderedQuery = new RenderedQuery("select 1", List.of());

        assertThatThrownBy(() -> MortarBoundQuery.of(" ", renderedQuery, ClientRow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("queryName cannot be blank");
    }

    private record ClientRow(Long id) {
    }
}
