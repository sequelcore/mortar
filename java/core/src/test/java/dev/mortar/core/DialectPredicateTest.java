package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

final class DialectPredicateTest {
    @Test
    void dialectPredicateCopiesParametersAndOptions() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<String> name = clients.column("name", "name", String.class);
        List<Parameter> parameters = new java.util.ArrayList<>();
        parameters.add(Parameter.of("Ada"));
        Map<String, String> options = new java.util.HashMap<>();
        options.put("mode", "websearch");

        Predicate.DialectPredicate predicate = new Predicate.DialectPredicate(
            "postgres",
            "full_text",
            name,
            parameters,
            options
        );
        parameters.add(Parameter.of("Grace"));
        options.put("mode", "plain");

        assertThat(predicate.parameters())
            .extracting(Parameter::value)
            .containsExactly("Ada");
        assertThat(predicate.options()).containsEntry("mode", "websearch");
    }

    @Test
    void dialectPredicateRejectsBlankNames() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<String> name = clients.column("name", "name", String.class);

        assertThatThrownBy(() -> new Predicate.DialectPredicate("", "operator", name, List.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("dialect cannot be blank");
        assertThatThrownBy(() -> new Predicate.DialectPredicate("postgres", "", name, List.of(), Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("operator cannot be blank");
    }

    @Test
    void queryMetadataIncludesDialectPredicateColumn() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<String> profile = clients.column("profile", "profile", String.class);
        QuerySpec query = new SimpleMortarDb()
            .from(clients)
            .where(new Predicate.DialectPredicate("postgres", "jsonb_contains", profile, List.of(), Map.of()))
            .build();

        QueryMetadata metadata = QueryMetadata.from(query);

        assertThat(metadata.tables()).containsExactly(clients);
        assertThat(metadata.columns()).containsExactly(profile);
    }
}
