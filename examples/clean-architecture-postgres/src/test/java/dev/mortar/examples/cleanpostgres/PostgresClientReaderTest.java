package dev.mortar.examples.cleanpostgres;

import static dev.mortar.examples.cleanpostgres.QClient.CLIENT;
import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.MortarBoundScalar;
import dev.mortar.core.QuerySpec;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;
import dev.mortar.testkit.MortarSqlAssertions;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class PostgresClientReaderTest {
    private final PostgresQueryRenderer renderer = new PostgresQueryRenderer();

    @Test
    void generatedPrimaryKeyLookupStaysInsideInfrastructureAdapter() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOptional(anyFindByIdQuery()))
            .thenReturn(Optional.of(new QClient.FindByIdRow(7L, "Ada", true)));
        ClientReader reader = new PostgresClientReader(jdbcClient, renderer);

        Optional<ClientSummary> result = reader.findById(7L);

        assertThat(result).contains(new ClientSummary(7L, "Ada"));
        verify(jdbcClient).fetchOptional(anyFindByIdQuery());
    }

    @Test
    void generatedPrimaryKeyLookupExposesStableSqlAtAdapterBoundary() {
        MortarBoundQuery<QClient.FindByIdRow> query = CLIENT.read(renderer)
            .findById(7L)
            .named("PostgresClientReader.findById");

        assertThatSql(query)
            .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
            .hasParameters(7L)
            .hasParameterTypes(Long.class);
    }

    @Test
    void activePageQueryUsesDslWithStableSql() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(any(QuerySpec.class), eq(ClientSummary.class)))
            .thenReturn(List.of(new ClientSummary(7L, "Ada")));
        PostgresClientReader reader = new PostgresClientReader(jdbcClient, renderer);

        List<ClientSummary> result = reader.findActivePage(1, 20);

        assertThat(result).containsExactly(new ClientSummary(7L, "Ada"));
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(ClientSummary.class));
        MortarSqlAssertions.assertThatSql(renderer.render(query.getValue()))
            .hasSql("select c.id, c.name from clients c where c.active = ? order by c.id asc limit ? offset ?")
            .hasParameters(true, 20, 20);
    }

    @Test
    void scalarReadsStayInsideInfrastructureAdapter() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOne(anyScalar()))
            .thenAnswer(invocation -> {
                MortarBoundScalar<?> scalar = invocation.getArgument(0);
                if (scalar.scalarType().equals(Long.class)) {
                    return 2L;
                }
                return true;
            });
        ClientReader reader = new PostgresClientReader(jdbcClient, renderer);

        assertThat(reader.countActive()).isEqualTo(2L);
        assertThat(reader.existsActive(7L)).isTrue();
        verify(jdbcClient, times(2)).fetchOne(anyScalar());
    }

    @Test
    void scalarSqlIsVisibleAtAdapterBoundary() {
        PostgresClientReader reader = new PostgresClientReader(mock(MortarJdbcClient.class), renderer);

        assertThatSql(reader.countActiveQuery())
            .hasName("PostgresClientReader.countActive")
            .hasSql("select count(*) from clients c where c.active = ?")
            .hasParameters(true);
        assertThatSql(reader.existsActiveQuery(7L))
            .hasName("PostgresClientReader.existsActive")
            .hasSql("select exists (select 1 from clients c where c.id = ? and c.active = ?)")
            .hasParameters(7L, true);
    }

    @SuppressWarnings("unchecked")
    private MortarBoundQuery<QClient.FindByIdRow> anyFindByIdQuery() {
        return any(MortarBoundQuery.class);
    }

    private <T> MortarBoundScalar<T> anyScalar() {
        return any();
    }
}
