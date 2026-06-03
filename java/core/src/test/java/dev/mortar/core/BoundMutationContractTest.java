package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class BoundMutationContractTest {
    @Test
    void rowCountMutationExposesNameSqlParametersTypesAndMetadata() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        UpdateSpec update = new UpdateSpec(clients, List.of(Assignment.of(name, "Ada")), List.of(id.eq(7L)), List.of());

        MortarBoundMutation mutation = MortarBoundMutation.unnamed(update, renderer())
            .named("ClientRepository.rename");

        assertThat(mutation.mutationName()).contains("ClientRepository.rename");
        assertThat(mutation.resultMode()).isEqualTo(MutationResultMode.ROW_COUNT);
        assertThat(mutation.sql()).isEqualTo("update clients set name = ? where id = ?");
        assertThat(mutation.parameters()).containsExactly(Parameter.of("Ada"), Parameter.of(7L));
        assertThat(mutation.parameterTypes()).containsExactly(String.class, Long.class);
        assertThat(mutation.metadata().columns()).containsExactly(name, id);
    }

    @Test
    void returningMutationRequiresReturningColumnsAndExposesRowType() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        InsertSpec insert = new InsertSpec(clients, List.of(Assignment.of(name, "Ada")), List.of(id, name));

        MortarReturningMutation<ClientRow> mutation = MortarReturningMutation.unnamed(insert, renderer(), ClientRow.class)
            .named("ClientRepository.create");

        assertThat(mutation.mutationName()).contains("ClientRepository.create");
        assertThat(mutation.resultMode()).isEqualTo(MutationResultMode.RETURNING_ROWS);
        assertThat(mutation.rowType()).isEqualTo(ClientRow.class);
        assertThat(mutation.sql()).isEqualTo("insert into clients (name) values (?) returning id, name");
        assertThat(mutation.metadata().columns()).containsExactly(name, id);
    }

    @Test
    void rowCountMutationRejectsReturningColumnsAndReturningMutationRequiresThem() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);

        assertThatThrownBy(() -> MortarBoundMutation.unnamed(
                new UpdateSpec(clients, List.of(Assignment.of(name, "Ada")), List.of(id.eq(7L)), List.of(id)),
                renderer()
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("row-count mutations cannot declare returning columns");

        assertThatThrownBy(() -> MortarReturningMutation.unnamed(
                new InsertSpec(clients, List.of(Assignment.of(name, "Ada")), List.of()),
                renderer(),
                ClientRow.class
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("returning mutations require at least one returning column");
    }

    @Test
    void rowCountMutationRejectsReturningRowResultMode() {
        RenderedQuery rendered = new RenderedQuery("delete from clients", List.of(), QueryMetadata.empty());

        assertThatThrownBy(() -> new MortarBoundMutation(
                java.util.Optional.empty(),
                rendered,
                MutationResultMode.RETURNING_ROWS
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("row-count mutation result mode must be ROW_COUNT");
    }

    private QueryRenderer renderer() {
        return new QueryRenderer() {
            @Override
            public RenderedQuery render(QuerySpec query) {
                throw new AssertionError("query renderer should not render row queries");
            }

            @Override
            public RenderedQuery render(InsertSpec insert) {
                return new RenderedQuery(
                    "insert into clients (name) values (?) returning id, name",
                    insert.assignments().stream().map(Assignment::value).toList(),
                    QueryMetadata.from(insert)
                );
            }

            @Override
            public RenderedQuery render(UpdateSpec update) {
                return new RenderedQuery(
                    "update clients set name = ? where id = ?",
                    List.of(Parameter.of("Ada"), Parameter.of(7L)),
                    QueryMetadata.from(update)
                );
            }
        };
    }

    private record ClientRow(Long id, String name) {
    }
}
