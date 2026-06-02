package dev.mortar.testkit;

import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Join;
import dev.mortar.core.JoinType;
import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.Parameter;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.TableRef;

import org.junit.jupiter.api.Test;

import java.util.List;

final class MortarSqlAssertionsTest {
    @Test
    void assertsRenderedSql() {
        assertThatSql(new RenderedQuery("select 1", List.of()))
            .hasSql("select 1")
            .renders("select 1");
    }

    @Test
    void assertsBoundQuerySql() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        MortarBoundQuery<ClientRow> query = MortarBoundQuery.of(
            "ClientRepository.findById",
            new RenderedQuery(
                "select c.id from clients c where c.id = ?",
                List.of(Parameter.of(7L)),
                new QueryMetadata(List.of(clients), List.of(id), List.of())
            ),
            ClientRow.class
        );

        assertThatSql(query)
            .hasSql("select c.id from clients c where c.id = ?")
            .hasParameters(7L)
            .hasParameterTypes(Long.class)
            .hasTables(clients)
            .hasColumns(id);
    }

    @Test
    void reportsBoundQueryParameterMismatchWithQueryContext() {
        MortarBoundQuery<ClientRow> query = MortarBoundQuery.of(
            "ClientRepository.findById",
            new RenderedQuery("select c.id from clients c where c.id = ?", List.of(Parameter.of(7L))),
            ClientRow.class
        );

        assertThatThrownBy(() -> assertThatSql(query).hasParameters(8L))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected SQL parameters to be:
                  <[8]>
                but were:
                  <[7]>
                SQL:
                  <select c.id from clients c where c.id = ?>
                Query:
                  <ClientRepository.findById>
                Row type:
                  <dev.mortar.testkit.MortarSqlAssertionsTest$ClientRow>""");
    }

    @Test
    void reportsRenderedSqlMismatch() {
        assertThatThrownBy(() -> assertThatSql(new RenderedQuery("select 1", List.of())).hasSql("select 2"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected SQL to be:
                  <select 2>
                but was:
                  <select 1>""");
    }

    @Test
    void assertsParameterValues() {
        RenderedQuery renderedQuery = new RenderedQuery(
            "select * from clients where id = ? and deleted_at is ?",
            List.of(Parameter.of(7L), new Parameter(null, Void.class))
        );

        assertThatSql(renderedQuery)
            .hasParameters(7L, null);
    }

    @Test
    void reportsParameterValueMismatch() {
        RenderedQuery renderedQuery = new RenderedQuery(
            "select * from clients where id = ?",
            List.of(Parameter.of(7L))
        );

        assertThatThrownBy(() -> assertThatSql(renderedQuery).hasParameters(8L))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected SQL parameters to be:
                  <[8]>
                but were:
                  <[7]>
                SQL:
                  <select * from clients where id = ?>""");
    }

    @Test
    void assertsParameterTypes() {
        RenderedQuery renderedQuery = new RenderedQuery(
            "select * from clients where id = ? and name = ?",
            List.of(Parameter.of(7L), Parameter.of("Ana"))
        );

        assertThatSql(renderedQuery)
            .hasParameterTypes(Long.class, String.class);
    }

    @Test
    void reportsParameterTypeMismatch() {
        RenderedQuery renderedQuery = new RenderedQuery(
            "select * from clients where id = ?",
            List.of(Parameter.of(7L))
        );

        assertThatThrownBy(() -> assertThatSql(renderedQuery).hasParameterTypes(Integer.class))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected SQL parameter types to be:
                  <[java.lang.Integer]>
                but were:
                  <[java.lang.Long]>
                SQL:
                  <select * from clients where id = ?>""");
    }

    @Test
    void assertsQueryMetadata() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> clientId = clients.column("id", "id", Long.class);
        ColumnRef<Long> routeClientId = routes.column("clientId", "client_id", Long.class);
        Join join = new Join(JoinType.INNER, routes, clientId, routeClientId);
        RenderedQuery renderedQuery = new RenderedQuery(
            "select c.id from clients c join routes r on c.id = r.client_id",
            List.of(),
            new QueryMetadata(List.of(clients, routes), List.of(clientId, routeClientId), List.of(join))
        );

        assertThatSql(renderedQuery)
            .hasTables(clients, routes)
            .hasColumns(clientId, routeClientId)
            .hasJoins(join);
    }

    @Test
    void reportsQueryMetadataMismatch() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        RenderedQuery renderedQuery = new RenderedQuery(
            "select c.id from clients c",
            List.of(),
            new QueryMetadata(List.of(clients), List.of(), List.of())
        );

        assertThatThrownBy(() -> assertThatSql(renderedQuery).hasTables(routes))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected query metadata tables to be:
                  <[TableRef[tableName=routes, alias=r]]>
                but were:
                  <[TableRef[tableName=clients, alias=c]]>
                SQL:
                  <select c.id from clients c>""");
    }

    @Test
    void rejectsNullRenderedQuery() {
        assertThatThrownBy(() -> assertThatSql((RenderedQuery) null).hasSql("select 1"))
            .isInstanceOf(AssertionError.class);
    }

    private record ClientRow(Long id) {
    }
}
