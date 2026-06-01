package dev.mortar.examples.springpostgres;

import static dev.mortar.examples.springpostgres.QClient.CLIENT;

import dev.mortar.core.MortarDb;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.SimpleMortarDb;
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
        return jdbcClient.fetchOptional(CLIENT.findById(renderer), new QClient.FindByIdParameters(id))
            .map(row -> new ClientSummary(row.id(), row.name()));
    }

    public Optional<ClientSummary> findActiveById(long id) {
        List<ClientSummary> rows = jdbcClient.fetch(findActiveByIdQuery(id), ClientSummary.class);
        return rows.stream().findFirst();
    }

    QuerySpec findActiveByIdQuery(long id) {
        return db.from(CLIENT)
            .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
            .where(client -> client.id.eq(id))
            .where(client -> client.active.eq(true))
            .named("ClientRepository.findActiveById")
            .build();
    }
}
