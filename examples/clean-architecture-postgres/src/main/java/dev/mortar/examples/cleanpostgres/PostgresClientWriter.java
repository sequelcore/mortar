package dev.mortar.examples.cleanpostgres;

import static dev.mortar.examples.cleanpostgres.QClient.CLIENT;

import dev.mortar.core.Assignment;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.MortarBoundMutation;
import dev.mortar.core.MortarReturningMutation;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.UpdateSpec;
import dev.mortar.jdbc.MortarJdbcClient;

import java.util.List;
import java.util.Optional;

public final class PostgresClientWriter implements ClientWriter {
    private final MortarJdbcClient jdbcClient;
    private final QueryRenderer renderer;

    public PostgresClientWriter(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
        this.jdbcClient = jdbcClient;
        this.renderer = renderer;
    }

    @Override
    public Optional<ClientSummary> create(long id, String name, boolean active) {
        List<ClientSummary> rows = jdbcClient.fetch(createMutation(id, name, active));
        if (rows.size() != 1) {
            throw new IllegalStateException("expected exactly one row from PostgresClientWriter.create");
        }
        return Optional.of(rows.getFirst());
    }

    @Override
    public int rename(long id, String name) {
        return jdbcClient.execute(renameMutation(id, name));
    }

    @Override
    public int delete(long id) {
        return jdbcClient.execute(deleteMutation(id));
    }

    MortarReturningMutation<ClientSummary> createMutation(long id, String name, boolean active) {
        return MortarReturningMutation.unnamed(
                new InsertSpec(
                    CLIENT.table,
                    List.of(
                        Assignment.of(CLIENT.id, id),
                        Assignment.of(CLIENT.name, name),
                        Assignment.of(CLIENT.active, active)
                    ),
                    List.of(CLIENT.id, CLIENT.name)
                ),
                renderer,
                ClientSummary.class
            )
            .named("PostgresClientWriter.create");
    }

    MortarBoundMutation renameMutation(long id, String name) {
        return MortarBoundMutation.unnamed(
                new UpdateSpec(
                    CLIENT.table,
                    List.of(Assignment.of(CLIENT.name, name)),
                    List.of(CLIENT.id.eq(id)),
                    List.of()
                ),
                renderer
            )
            .named("PostgresClientWriter.rename");
    }

    MortarBoundMutation deleteMutation(long id) {
        return MortarBoundMutation.unnamed(
                new DeleteSpec(CLIENT.table, List.of(CLIENT.id.eq(id)), List.of()),
                renderer
            )
            .named("PostgresClientWriter.delete");
    }
}
