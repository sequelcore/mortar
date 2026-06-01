package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;

final class UnsafeRawSqlTest {
    @Test
    void modelsExplicitUnsafeRawSqlPredicate() {
        Predicate predicate = Predicate.unsafeRaw("c.deleted_at is null", List.of());

        assertThat(predicate)
            .isEqualTo(new Predicate.RawSqlPredicate("c.deleted_at is null", List.of()));
    }

    @Test
    void queryBuilderExposesExplicitUnsafeRawWhere() {
        TableRef clients = new TableRef("clients", "c");

        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .unsafeWhereRaw("c.score > ?", Parameter.of(10))
            .build();

        assertThat(query.predicates()).containsExactly(
            new Predicate.RawSqlPredicate("c.score > ?", List.of(Parameter.of(10)))
        );
    }

    @Test
    void rejectsBlankRawSql() {
        assertThatThrownBy(() -> Predicate.unsafeRaw(" ", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("sql cannot be blank");
    }
}
