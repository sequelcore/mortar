package dev.mortar.examples.cleanpostgres;

import static dev.mortar.examples.cleanpostgres.QClient.CLIENT;

import dev.mortar.core.MortarDb;
import dev.mortar.core.MortarBoundScalar;
import dev.mortar.core.MortarPage;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.jdbc.MortarJdbcClient;

import java.util.List;
import java.util.Optional;

public final class PostgresClientReader implements ClientReader {
    private final MortarJdbcClient jdbcClient;
    private final QueryRenderer renderer;
    private final MortarDb db;

    public PostgresClientReader(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
        this(jdbcClient, renderer, new SimpleMortarDb());
    }

    PostgresClientReader(MortarJdbcClient jdbcClient, QueryRenderer renderer, MortarDb db) {
        this.jdbcClient = jdbcClient;
        this.renderer = renderer;
        this.db = db;
    }

    @Override
    public Optional<ClientSummary> findById(long id) {
        return jdbcClient.fetchOptional(
                CLIENT.read(renderer)
                    .findById(id)
                    .named("PostgresClientReader.findById")
            )
            .map(row -> new ClientSummary(row.id(), row.name()));
    }

    @Override
    public List<ClientSummary> findActivePage(int page, int size) {
        return jdbcClient.fetch(activePageQuery(page, size), ClientSummary.class);
    }

    @Override
    public long countActive() {
        return jdbcClient.fetchOne(countActiveQuery());
    }

    @Override
    public boolean existsActive(long id) {
        return jdbcClient.fetchOne(existsActiveQuery(id));
    }

    QuerySpec activePageQuery(int page, int size) {
        return db.from(CLIENT)
            .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
            .where(client -> client.active.eq(true))
            .orderBy(client -> client.id.asc())
            .page(MortarPage.of(page, size))
            .named("PostgresClientReader.findActivePage")
            .build();
    }

    MortarBoundScalar<Long> countActiveQuery() {
        return db.from(CLIENT)
            .where(client -> client.active.eq(true))
            .count(renderer)
            .named("PostgresClientReader.countActive");
    }

    MortarBoundScalar<Boolean> existsActiveQuery(long id) {
        return db.from(CLIENT)
            .where(client -> client.id.eq(id))
            .where(client -> client.active.eq(true))
            .exists(renderer)
            .named("PostgresClientReader.existsActive");
    }
}
