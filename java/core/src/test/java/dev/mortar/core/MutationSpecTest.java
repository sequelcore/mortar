package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

final class MutationSpecTest {
    @Test
    void assignmentUsesColumnTypeForNullValues() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<String> name = clients.column("name", "name", String.class);

        Assignment<String> assignment = Assignment.of(name, null);

        assertThat(assignment.value().value()).isNull();
        assertThat(assignment.value().javaType()).isEqualTo(String.class);
    }

    @Test
    void insertSpecRequiresAtLeastOneAssignmentAndCopiesLists() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        List<Assignment<?>> assignments = new ArrayList<>();
        assignments.add(Assignment.of(id, 1L));
        List<ColumnRef<?>> returning = new ArrayList<>();
        returning.add(id);

        InsertSpec insert = new InsertSpec(clients, assignments, returning);
        assignments.add(Assignment.of(name, "Ada"));
        returning.add(name);

        assertThat(insert.assignments()).hasSize(1);
        assertThat(insert.assignments().getFirst().column()).isEqualTo(id);
        assertThat(insert.returning()).containsExactly(id);
        assertThatThrownBy(() -> new InsertSpec(clients, List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("assignments cannot be empty");
    }

    @Test
    void updateSpecRequiresAtLeastOneAssignmentAndCopiesPredicates() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(id.eq(1L));

        UpdateSpec update = new UpdateSpec(clients, List.of(Assignment.of(name, "Ada")), predicates, List.of(id));
        predicates.add(name.isNotNull());

        assertThat(update.assignments()).hasSize(1);
        assertThat(update.assignments().getFirst().column()).isEqualTo(name);
        assertThat(update.predicates()).containsExactly(id.eq(1L));
        assertThat(update.returning()).containsExactly(id);
        assertThatThrownBy(() -> new UpdateSpec(clients, List.of(), List.of(), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("assignments cannot be empty");
    }

    @Test
    void mutationMetadataIncludesAssignedFilteredAndReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        UpdateSpec update = new UpdateSpec(
            clients,
            List.of(Assignment.of(name, "Ada")),
            List.of(id.eq(1L)),
            List.of(id, name)
        );

        QueryMetadata metadata = QueryMetadata.from(update);

        assertThat(metadata.tables()).containsExactly(clients);
        assertThat(metadata.columns()).containsExactly(name, id);
        assertThat(metadata.joins()).isEmpty();
    }

    @Test
    void deleteMetadataIncludesFilteredAndReturningColumns() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        DeleteSpec delete = new DeleteSpec(clients, List.of(name.containsIgnoreCase("ada")), List.of(id));

        QueryMetadata metadata = QueryMetadata.from(delete);

        assertThat(metadata.tables()).containsExactly(clients);
        assertThat(metadata.columns()).containsExactly(name, id);
        assertThat(metadata.joins()).isEmpty();
    }

    @Test
    void mutationSpecsRejectColumnsFromDifferentTables() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        TableRef clientsAlias = new TableRef("clients", "client_alias");
        ColumnRef<Long> clientId = clients.column("id", "id", Long.class);
        ColumnRef<Long> routeId = routes.column("id", "id", Long.class);
        ColumnRef<Long> clientAliasId = clientsAlias.column("id", "id", Long.class);

        assertThatThrownBy(() -> new InsertSpec(clients, List.of(Assignment.of(routeId, 1L)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("column id belongs to table routes, not clients");
        assertThatThrownBy(() -> new InsertSpec(clients, List.of(Assignment.of(clientAliasId, 1L)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("column id belongs to table clients as client_alias, not clients as c");
        assertThatThrownBy(() -> new InsertSpec(clients, List.of(Assignment.of(clientId, 1L)), List.of(routeId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("column id belongs to table routes, not clients");
        assertThatThrownBy(() -> new UpdateSpec(clients, List.of(Assignment.of(clientId, 1L)), List.of(routeId.eq(1L)), List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("column id belongs to table routes, not clients");
        assertThatThrownBy(() -> new DeleteSpec(clients, List.of(), List.of(routeId)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("column id belongs to table routes, not clients");
    }
}
