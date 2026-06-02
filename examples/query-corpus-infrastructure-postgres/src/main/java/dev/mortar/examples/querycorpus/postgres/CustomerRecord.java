package dev.mortar.examples.querycorpus.postgres;

import dev.mortar.processor.MortarColumn;
import dev.mortar.processor.MortarEntity;
import dev.mortar.processor.MortarId;

@MortarEntity(table = "customers", alias = "cu")
final class CustomerRecord {
    @MortarId
    @MortarColumn(name = "id", nullable = false)
    Long id;

    @MortarColumn(name = "name", nullable = false)
    String name;
}
