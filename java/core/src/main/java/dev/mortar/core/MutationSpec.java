package dev.mortar.core;

import java.util.List;

/**
 * Common marker for immutable mutation specifications.
 */
public sealed interface MutationSpec permits DeleteSpec, InsertSpec, UpdateSpec {
    TableRef table();

    List<ColumnRef<?>> returning();
}
