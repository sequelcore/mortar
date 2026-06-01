package dev.mortar.examples.springpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mortar.core.QuerySpec;
import dev.mortar.jdbc.MortarGeneratedQuery;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;
import dev.mortar.testkit.MortarSqlAssertions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClientRepositoryTest {
    private final PostgresQueryRenderer renderer = new PostgresQueryRenderer();

    @Test
    void buildsTransparentSqlForGeneratedFindById() {
        QClient.FindByIdQuery query = QClient.CLIENT.findById(renderer);

        assertThat(query.sql()).isEqualTo("select c.id, c.name, c.active from clients c where c.id = ?");
        assertThat(query.parameterTypes()).containsExactly(Long.class);
    }

    @Test
    void buildsTransparentSqlForGeneratedFindAll() {
        QClient.FindAllQuery query = QClient.CLIENT.findAll(renderer);

        assertThat(query.sql()).isEqualTo("select c.id, c.name, c.active from clients c");
        assertThat(query.parameterTypes()).isEmpty();
    }

    @Test
    void buildsTransparentSqlForFindActiveById() {
        ClientRepository repository = new ClientRepository(mock(MortarJdbcClient.class), renderer);
        QuerySpec query = repository.findActiveByIdQuery(7L);

        MortarSqlAssertions.assertThatSql(renderer.render(query))
            .hasSql("select c.id, c.name from clients c where c.id = ? and c.active = ?")
            .hasParameters(7L, true);
    }

    @Test
    void fetchesClientByIdWithGeneratedExecutor() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOptional(anyFindByIdQuery(), eq(new QClient.FindByIdParameters(7L))))
            .thenReturn(Optional.of(new QClient.FindByIdRow(7L, "Ada", true)));
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        Optional<ClientSummary> result = repository.findById(7L);

        assertThat(result).contains(new ClientSummary(7L, "Ada"));
        verify(jdbcClient).fetchOptional(anyFindByIdQuery(), eq(new QClient.FindByIdParameters(7L)));
    }

    @Test
    void fetchesAllClientsWithGeneratedExecutor() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(anyFindAllQuery(), eq(new QClient.FindAllParameters())))
            .thenReturn(List.of(
                new QClient.FindAllRow(7L, "Ada", true),
                new QClient.FindAllRow(8L, "Grace", false)
            ));
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        List<ClientSummary> result = repository.findAll();

        assertThat(result).containsExactly(
            new ClientSummary(7L, "Ada"),
            new ClientSummary(8L, "Grace")
        );
        verify(jdbcClient).fetch(anyFindAllQuery(), eq(new QClient.FindAllParameters()));
    }

    @Test
    void fetchesOneActiveClientById() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(any(QuerySpec.class), eq(ClientSummary.class)))
            .thenReturn(List.of(new ClientSummary(7L, "Ada")));
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        Optional<ClientSummary> result = repository.findActiveById(7L);

        assertThat(result).contains(new ClientSummary(7L, "Ada"));
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(ClientSummary.class));
        assertThat(query.getValue().name()).contains("ClientRepository.findActiveById");
    }

    @SuppressWarnings("unchecked")
    private MortarGeneratedQuery<QClient.FindByIdParameters, QClient.FindByIdRow> anyFindByIdQuery() {
        return any(MortarGeneratedQuery.class);
    }

    @SuppressWarnings("unchecked")
    private MortarGeneratedQuery<QClient.FindAllParameters, QClient.FindAllRow> anyFindAllQuery() {
        return any(MortarGeneratedQuery.class);
    }
}
