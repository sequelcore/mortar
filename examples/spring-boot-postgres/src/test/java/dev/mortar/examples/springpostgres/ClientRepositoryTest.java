package dev.mortar.examples.springpostgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.MortarBoundMutation;
import dev.mortar.core.MortarBoundScalar;
import dev.mortar.core.MortarReturningMutation;
import dev.mortar.core.Parameter;
import dev.mortar.core.QuerySpec;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class ClientRepositoryTest {
    private final PostgresQueryRenderer renderer = new PostgresQueryRenderer();

    @Test
    void buildsTransparentSqlForGeneratedFindById() {
        MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT.read(renderer)
            .findById(7L)
            .named("ClientRepository.findById");

        assertThatSql(query)
            .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
            .hasParameters(7L)
            .hasParameterTypes(Long.class)
            .hasTables(QClient.CLIENT.table)
            .hasColumns(QClient.CLIENT.id, QClient.CLIENT.name, QClient.CLIENT.active);
        assertThat(query.parameters()).containsExactly(Parameter.of(7L));
        assertThat(query.parameterTypes()).containsExactly(Long.class);
        assertThat(query.queryName()).contains("ClientRepository.findById");
        assertThat(query.rowType()).isEqualTo(QClient.FindByIdRow.class);
    }

    @Test
    void rejectsNullGeneratedFindByIdBeforeRenderingSql() {
        assertThatThrownBy(() -> QClient.CLIENT.read(renderer).findById(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("id cannot be null");
    }

    @Test
    void buildsTransparentSqlForGeneratedFindAll() {
        MortarBoundQuery<QClient.FindAllRow> query = QClient.CLIENT.read(renderer)
            .findAll()
            .named("ClientRepository.findAll");

        assertThat(query.sql()).isEqualTo("select c.id, c.name, c.active from clients c");
        assertThat(query.parameterTypes()).isEmpty();
        assertThat(query.queryName()).contains("ClientRepository.findAll");
    }

    @Test
    void buildsTransparentSqlForFindActiveById() {
        ClientRepository repository = new ClientRepository(mock(MortarJdbcClient.class), renderer);
        QuerySpec query = repository.findActiveByIdQuery(7L);

        assertThatSql(renderer.render(query))
            .hasSql("select c.id, c.name from clients c where c.id = ? and c.active = ?")
            .hasParameters(7L, true);
    }

    @Test
    void buildsTransparentSqlForScalarAndMutationContracts() {
        ClientRepository repository = new ClientRepository(mock(MortarJdbcClient.class), renderer);

        assertThatSql(repository.countActiveQuery())
            .hasName("ClientRepository.countActive")
            .hasSql("select count(*) from clients c where c.active = ?")
            .hasParameters(true)
            .hasParameterTypes(Boolean.class);
        assertThatSql(repository.existsActiveQuery(7L))
            .hasName("ClientRepository.existsActive")
            .hasSql("select exists (select 1 from clients c where c.id = ? and c.active = ?)")
            .hasParameters(7L, true)
            .hasParameterTypes(Long.class, Boolean.class);
        assertThatSql(repository.deactivateMutation(7L))
            .hasName("ClientRepository.deactivate")
            .hasSql("update clients set active = ? where id = ?")
            .hasParameters(false, 7L)
            .hasParameterTypes(Boolean.class, Long.class);
        assertThatSql(repository.deleteMutation(7L))
            .hasName("ClientRepository.delete")
            .hasSql("delete from clients where id = ?")
            .hasParameters(7L)
            .hasParameterTypes(Long.class);
    }

    @Test
    void buildsTransparentSqlForCreateReturningMutation() {
        ClientRepository repository = new ClientRepository(mock(MortarJdbcClient.class), renderer);

        assertThatSql(repository.createMutation(9L, "Lin", true))
            .hasName("ClientRepository.create")
            .hasSql("insert into clients (id, name, active) values (?, ?, ?) returning id, name")
            .hasParameters(9L, "Lin", true)
            .hasParameterTypes(Long.class, String.class, Boolean.class);
    }

    @Test
    void fetchesClientByIdWithGeneratedExecutor() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOptional(anyFindByIdQuery()))
            .thenReturn(Optional.of(new QClient.FindByIdRow(7L, "Ada", true)));
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        Optional<ClientSummary> result = repository.findById(7L);

        assertThat(result).contains(new ClientSummary(7L, "Ada"));
        verify(jdbcClient).fetchOptional(anyFindByIdQuery());
    }

    @Test
    void fetchesAllClientsWithGeneratedExecutor() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(anyFindAllQuery()))
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
        verify(jdbcClient).fetch(anyFindAllQuery());
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

    @Test
    void executesScalarAndMutationRepositoryMethodsThroughJdbcClient() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOne(anyScalar()))
            .thenAnswer(invocation -> {
                MortarBoundScalar<?> scalar = invocation.getArgument(0);
                if (scalar.scalarType().equals(Long.class)) {
                    return 2L;
                }
                return true;
            });
        when(jdbcClient.fetch(anyReturningClientSummaryMutation()))
            .thenReturn(List.of(new ClientSummary(9L, "Lin")));
        when(jdbcClient.execute(anyBoundMutation())).thenReturn(1);
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        assertThat(repository.countActive()).isEqualTo(2L);
        assertThat(repository.existsActive(7L)).isTrue();
        assertThat(repository.create(9L, "Lin", true)).contains(new ClientSummary(9L, "Lin"));
        assertThat(repository.deactivate(7L)).isEqualTo(1);
        assertThat(repository.delete(7L)).isEqualTo(1);
        verify(jdbcClient, times(2)).fetchOne(anyScalar());
        verify(jdbcClient).fetch(anyReturningClientSummaryMutation());
        verify(jdbcClient, times(2)).execute(anyBoundMutation());
    }

    @Test
    void createFailsFastWhenReturningMutationDoesNotReturnExactlyOneRow() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        ClientRepository repository = new ClientRepository(jdbcClient, renderer);

        when(jdbcClient.fetch(anyReturningClientSummaryMutation())).thenReturn(List.of());
        assertThatThrownBy(() -> repository.create(9L, "Lin", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected exactly one row from ClientRepository.create");

        when(jdbcClient.fetch(anyReturningClientSummaryMutation()))
            .thenReturn(List.of(new ClientSummary(9L, "Lin"), new ClientSummary(10L, "Lina")));
        assertThatThrownBy(() -> repository.create(9L, "Lin", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected exactly one row from ClientRepository.create");
    }

    @SuppressWarnings("unchecked")
    private MortarBoundQuery<QClient.FindByIdRow> anyFindByIdQuery() {
        return any(MortarBoundQuery.class);
    }

    @SuppressWarnings("unchecked")
    private MortarBoundQuery<QClient.FindAllRow> anyFindAllQuery() {
        return any(MortarBoundQuery.class);
    }

    private MortarBoundMutation anyBoundMutation() {
        return any(MortarBoundMutation.class);
    }

    private <T> MortarBoundScalar<T> anyScalar() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private MortarReturningMutation<ClientSummary> anyReturningClientSummaryMutation() {
        return any(MortarReturningMutation.class);
    }
}
