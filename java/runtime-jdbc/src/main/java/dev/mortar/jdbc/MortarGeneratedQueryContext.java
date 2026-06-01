package dev.mortar.jdbc;

import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;

import java.util.List;
import java.util.Objects;

final class MortarGeneratedQueryContext {
    private MortarGeneratedQueryContext() {
    }

    static RenderedQuery rendered(MortarGeneratedQuery<?, ?> query) {
        Objects.requireNonNull(query, "query cannot be null");

        return new RenderedQuery(
            query.sql(),
            query.parameterTypes().stream()
                .map(type -> new Parameter(null, type))
                .toList(),
            query.metadata()
        );
    }

    static List<MortarJdbcParameter> redactedParameters(MortarGeneratedQuery<?, ?> query) {
        Objects.requireNonNull(query, "query cannot be null");

        return query.parameterTypes().stream()
            .map(MortarJdbcParameter::redacted)
            .toList();
    }
}
