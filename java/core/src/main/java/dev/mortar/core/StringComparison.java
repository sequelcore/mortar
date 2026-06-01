package dev.mortar.core;

import java.util.Objects;
import java.util.Optional;

public record StringComparison(StringCaseStrategy caseStrategy, Optional<String> collation) {
    public StringComparison {
        Objects.requireNonNull(caseStrategy, "caseStrategy cannot be null");
        Objects.requireNonNull(collation, "collation cannot be null");
        collation.ifPresent(value -> {
            if (value.isBlank()) {
                throw new IllegalArgumentException("collation cannot be blank");
            }
        });
    }

    public static StringComparison caseSensitive() {
        return new StringComparison(StringCaseStrategy.SENSITIVE, Optional.empty());
    }

    public static StringComparison caseInsensitive() {
        return new StringComparison(StringCaseStrategy.INSENSITIVE, Optional.empty());
    }

    public static StringComparison caseInsensitive(String collation) {
        return new StringComparison(StringCaseStrategy.INSENSITIVE, Optional.of(collation));
    }
}
