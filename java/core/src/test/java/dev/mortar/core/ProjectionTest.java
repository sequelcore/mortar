package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class ProjectionTest {
    private final TableRef clients = new TableRef("clients", "c");
    private final TableRef routes = new TableRef("routes", "r");
    private final ColumnRef<Long> id = clients.column("id", "id", Long.class);
    private final ColumnRef<String> name = clients.column("name", "name", String.class);
    private final ColumnRef<String> routeName = routes.column("name", "name", String.class);

    @Test
    void modelsScalarProjection() {
        Projection projection = Projection.scalar(name);

        assertThat(projection.kind()).isEqualTo(ProjectionKind.SCALAR);
        assertThat(projection.columns()).containsExactly(name);
        assertThat(projection.targetType()).isEmpty();
        assertThat(projection.children()).isEmpty();
    }

    @Test
    void modelsRecordProjection() {
        Projection projection = Projection.record(ClientRow.class, List.of(id, name));

        assertThat(projection.kind()).isEqualTo(ProjectionKind.RECORD);
        assertThat(projection.targetType()).contains(ClientRow.class);
        assertThat(projection.columns()).containsExactly(id, name);
    }

    @Test
    void modelsDtoProjection() {
        Projection projection = Projection.dto(ClientDto.class, List.of(id, name));

        assertThat(projection.kind()).isEqualTo(ProjectionKind.DTO);
        assertThat(projection.targetType()).contains(ClientDto.class);
        assertThat(projection.columns()).containsExactly(id, name);
    }

    @Test
    void modelsNestedProjection() {
        Projection projection = Projection.nested(
            ClientWithRoute.class,
            List.of(id, name),
            List.of(Projection.record(RouteRow.class, List.of(routeName)))
        );

        assertThat(projection.kind()).isEqualTo(ProjectionKind.NESTED);
        assertThat(projection.targetType()).contains(ClientWithRoute.class);
        assertThat(projection.columns()).containsExactly(id, name);
        assertThat(projection.children()).hasSize(1);
    }

    @Test
    void rejectsEmptyProjectionColumns() {
        assertThatThrownBy(() -> Projection.record(ClientRow.class, List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("columns cannot be empty");
    }

    private record ClientRow(Long id, String name) {
    }

    private record RouteRow(String name) {
    }

    private static final class ClientDto {
    }

    private static final class ClientWithRoute {
    }
}
