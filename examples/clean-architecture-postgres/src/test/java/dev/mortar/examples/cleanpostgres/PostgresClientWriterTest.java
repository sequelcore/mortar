package dev.mortar.examples.cleanpostgres;

import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mortar.core.MortarBoundMutation;
import dev.mortar.core.MortarReturningMutation;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;

import org.junit.jupiter.api.Test;

import java.util.List;

final class PostgresClientWriterTest {
    private final PostgresQueryRenderer renderer = new PostgresQueryRenderer();

    @Test
    void createUpdateAndDeleteStayInsideInfrastructureAdapter() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(anyReturningSummaryMutation()))
            .thenReturn(List.of(new ClientSummary(9L, "Lin")));
        when(jdbcClient.execute(anyBoundMutation())).thenReturn(1);
        ClientWriter writer = new PostgresClientWriter(jdbcClient, renderer);

        assertThat(writer.create(9L, "Lin", true)).contains(new ClientSummary(9L, "Lin"));
        assertThat(writer.rename(9L, "Lina")).isEqualTo(1);
        assertThat(writer.delete(9L)).isEqualTo(1);
        verify(jdbcClient).fetch(anyReturningSummaryMutation());
        verify(jdbcClient, org.mockito.Mockito.times(2)).execute(anyBoundMutation());
    }

    @Test
    void createFailsFastWhenReturningMutationDoesNotReturnExactlyOneRow() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        ClientWriter writer = new PostgresClientWriter(jdbcClient, renderer);

        when(jdbcClient.fetch(anyReturningSummaryMutation())).thenReturn(List.of());
        assertThatThrownBy(() -> writer.create(9L, "Lin", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected exactly one row from PostgresClientWriter.create");

        when(jdbcClient.fetch(anyReturningSummaryMutation()))
            .thenReturn(List.of(new ClientSummary(9L, "Lin"), new ClientSummary(10L, "Lina")));
        assertThatThrownBy(() -> writer.create(9L, "Lin", true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("expected exactly one row from PostgresClientWriter.create");
    }

    @Test
    void mutationSqlIsVisibleAtAdapterBoundary() {
        PostgresClientWriter writer = new PostgresClientWriter(mock(MortarJdbcClient.class), renderer);

        assertThatSql(writer.createMutation(9L, "Lin", true))
            .hasName("PostgresClientWriter.create")
            .hasSql("insert into clients (id, name, active) values (?, ?, ?) returning id, name")
            .hasParameters(9L, "Lin", true);
        assertThatSql(writer.renameMutation(9L, "Lina"))
            .hasName("PostgresClientWriter.rename")
            .hasSql("update clients set name = ? where id = ?")
            .hasParameters("Lina", 9L);
        assertThatSql(writer.deleteMutation(9L))
            .hasName("PostgresClientWriter.delete")
            .hasSql("delete from clients where id = ?")
            .hasParameters(9L);
    }

    @SuppressWarnings("unchecked")
    private MortarReturningMutation<ClientSummary> anyReturningSummaryMutation() {
        return any(MortarReturningMutation.class);
    }

    private MortarBoundMutation anyBoundMutation() {
        return any(MortarBoundMutation.class);
    }
}
