package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class StringPredicateTest {
    private final TableRef clients = new TableRef("clients", "c");
    private final ColumnRef<String> name = clients.column("name", "name", String.class);

    @Test
    void createsExplicitCaseSensitiveStringPredicates() {
        Predicate predicate = name.contains("Ricardo", StringComparison.caseSensitive());

        assertThat(predicate).isEqualTo(
            new Predicate.StringPredicate(
                name,
                StringOperator.CONTAINS,
                "Ricardo",
                StringComparison.caseSensitive()
            )
        );
    }

    @Test
    void createsExplicitCaseInsensitiveStringPredicatesWithCollation() {
        Predicate predicate = name.startsWith("ric", StringComparison.caseInsensitive("und-x-icu"));

        assertThat(predicate).isEqualTo(
            new Predicate.StringPredicate(
                name,
                StringOperator.STARTS_WITH,
                "ric",
                StringComparison.caseInsensitive("und-x-icu")
            )
        );
    }

    @Test
    void preservesConvenienceContainsIgnoreCaseAsExplicitStrategy() {
        assertThat(name.containsIgnoreCase("rio")).isEqualTo(
            new Predicate.StringPredicate(
                name,
                StringOperator.CONTAINS,
                "rio",
                StringComparison.caseInsensitive()
            )
        );
    }

    @Test
    void rejectsBlankCollation() {
        assertThatThrownBy(() -> StringComparison.caseInsensitive(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("collation cannot be blank");
    }
}
