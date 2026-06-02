package dev.mortar.examples.querycorpus.postgres;

import dev.mortar.processor.MortarColumn;
import dev.mortar.processor.MortarEntity;
import dev.mortar.processor.MortarId;

@MortarEntity(table = "ticket_statuses", alias = "ts")
final class TicketStatusRecord {
    @MortarId
    @MortarColumn(name = "code", nullable = false)
    String code;

    @MortarColumn(name = "name", nullable = false)
    String name;
}
