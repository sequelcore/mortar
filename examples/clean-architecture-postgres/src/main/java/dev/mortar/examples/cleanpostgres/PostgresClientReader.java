package dev.mortar.examples.cleanpostgres;

import static dev.mortar.examples.cleanpostgres.QClient.CLIENT;

import dev.mortar.core.MortarDb;
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
        return jdbcClient.fetchOptional(CLIENT.findById(renderer), new QClient.FindByIdParameters(id))
            .map(row -> new ClientSummary(row.id(), row.name()));
    }

    @Override
    public List<ClientSummary> findActivePage(int page, int size) {
        return jdbcClient.fetch(activePageQuery(page, size), ClientSummary.class);
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
}
