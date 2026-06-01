package dev.mortar.examples.cleanpostgres;

import dev.mortar.processor.MortarColumn;
import dev.mortar.processor.MortarEntity;
import dev.mortar.processor.MortarId;

@MortarEntity(table = "clients", alias = "c")
final class Client {
    @MortarId
    @MortarColumn(name = "id", nullable = false)
    Long id;

    @MortarColumn(name = "name", nullable = false)
    String name;

    @MortarColumn(name = "active", nullable = false)
    Boolean active;
}
