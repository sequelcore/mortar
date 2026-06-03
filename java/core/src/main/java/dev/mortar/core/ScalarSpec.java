package dev.mortar.core;

import java.util.List;

/**
 * Common marker for immutable scalar query specifications.
 */
public sealed interface ScalarSpec<T> permits CountSpec, ExistsSpec {
    TableRef table();

    List<Join> joins();

    List<Predicate> predicates();

    Class<T> scalarType();
}
