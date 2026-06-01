package dev.mortar.core;

import java.util.List;

public sealed interface MutationSpec permits DeleteSpec, InsertSpec, UpdateSpec {
    TableRef table();

    List<ColumnRef<?>> returning();
}
