package dev.mortar.examples.springpostgres;

import static dev.mortar.examples.springpostgres.QClient.CLIENT;

import dev.mortar.core.Assignment;
import dev.mortar.core.DeleteSpec;
import dev.mortar.core.InsertSpec;
import dev.mortar.core.MortarDb;
import dev.mortar.core.MortarBoundMutation;
import dev.mortar.core.MortarBoundScalar;
import dev.mortar.core.MortarReturningMutation;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.UpdateSpec;
import dev.mortar.jdbc.MortarJdbcClient;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public final class ClientRepository {
    private final MortarJdbcClient jdbcClient;
    private final QueryRenderer renderer;
    private final MortarDb db;

    public ClientRepository(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
        this(jdbcClient, renderer, new SimpleMortarDb());
    }

    ClientRepository(MortarJdbcClient jdbcClient, QueryRenderer renderer, MortarDb db) {
        this.jdbcClient = jdbcClient;
        this.renderer = renderer;
        this.db = db;
    }

    public Optional<ClientSummary> findById(long id) {
        return jdbcClient.fetchOptional(
                CLIENT.read(renderer)
                    .findById(id)
                    .named("ClientRepository.findById")
            )
            .map(row -> new ClientSummary(row.id(), row.name()));
    }

    public List<ClientSummary> findAll() {
        return jdbcClient.fetch(
                CLIENT.read(renderer)
                    .findAll()
                    .named("ClientRepository.findAll")
            )
            .stream()
            .map(row -> new ClientSummary(row.id(), row.name()))
            .toList();
    }

    public Optional<ClientSummary> findActiveById(long id) {
        List<ClientSummary> rows = jdbcClient.fetch(findActiveByIdQuery(id), ClientSummary.class);
        return rows.stream().findFirst();
    }

    public long countActive() {
        return jdbcClient.fetchOne(countActiveQuery());
    }

    public boolean existsActive(long id) {
        return jdbcClient.fetchOne(existsActiveQuery(id));
    }

    public Optional<ClientSummary> create(long id, String name, boolean active) {
        List<ClientSummary> rows = jdbcClient.fetch(createMutation(id, name, active));
        if (rows.size() != 1) {
            throw new IllegalStateException("expected exactly one row from ClientRepository.create");
        }
        return Optional.of(rows.getFirst());
    }

    public int deactivate(long id) {
        return jdbcClient.execute(deactivateMutation(id));
    }

    public int delete(long id) {
        return jdbcClient.execute(deleteMutation(id));
    }

    QuerySpec findActiveByIdQuery(long id) {
        return db.from(CLIENT)
            .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
            .where(client -> client.id.eq(id))
            .where(client -> client.active.eq(true))
            .named("ClientRepository.findActiveById")
            .build();
    }

    MortarBoundScalar<Long> countActiveQuery() {
        return db.from(CLIENT)
            .where(client -> client.active.eq(true))
            .count(renderer)
            .named("ClientRepository.countActive");
    }

    MortarBoundScalar<Boolean> existsActiveQuery(long id) {
        return db.from(CLIENT)
            .where(client -> client.id.eq(id))
            .where(client -> client.active.eq(true))
            .exists(renderer)
            .named("ClientRepository.existsActive");
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
            .named("ClientRepository.create");
    }

    MortarBoundMutation deactivateMutation(long id) {
        return MortarBoundMutation.unnamed(
                new UpdateSpec(
                    CLIENT.table,
                    List.of(Assignment.of(CLIENT.active, false)),
                    List.of(CLIENT.id.eq(id)),
                    List.of()
                ),
                renderer
            )
            .named("ClientRepository.deactivate");
    }

    MortarBoundMutation deleteMutation(long id) {
        return MortarBoundMutation.unnamed(
                new DeleteSpec(CLIENT.table, List.of(CLIENT.id.eq(id)), List.of()),
                renderer
            )
            .named("ClientRepository.delete");
    }
}
