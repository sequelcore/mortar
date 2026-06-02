package dev.mortar.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;

import org.junit.jupiter.api.Test;

import java.util.List;

final class MortarJdbcBoundQueryTest {
    @Test
    void keepsJdbcRowMappingInRuntimeContract() throws Exception {
        MortarJdbcBoundQuery<ClientRow> query = MortarJdbcBoundQuery.of(
            MortarBoundQuery.of(
                "ClientRepository.findById",
                new RenderedQuery("select id from clients where id = ?", List.of(Parameter.of(7L))),
                ClientRow.class
            ),
            resultSet -> new ClientRow(resultSet.getLong("id"))
        );

        assertThat(query.queryName()).contains("ClientRepository.findById");
        assertThat(query.sql()).isEqualTo("select id from clients where id = ?");
        assertThat(query.parameterTypes()).containsExactly(Long.class);
        assertThat(query.rowType()).isEqualTo(ClientRow.class);
        assertThat(query.rowMapper()).isNotNull();
    }

    @Test
    void namedReturnsANewJdbcBoundQueryWithoutMutatingTheOriginal() {
        MortarJdbcBoundQuery<ClientRow> unnamed = MortarJdbcBoundQuery.of(
            MortarBoundQuery.unnamed(
                new RenderedQuery("select id from clients where id = ?", List.of(Parameter.of(7L))),
                ClientRow.class
            ),
            resultSet -> new ClientRow(resultSet.getLong("id"))
        );

        MortarJdbcBoundQuery<ClientRow> named = unnamed.named("ClientRepository.findById");

        assertThat(unnamed.queryName()).isEmpty();
        assertThat(named.queryName()).contains("ClientRepository.findById");
        assertThat(named.rowMapper()).isSameAs(unnamed.rowMapper());
    }

    private record ClientRow(Long id) {
    }
}
