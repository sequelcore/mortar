package dev.mortar.examples.querycorpus.postgres;

import dev.mortar.processor.MortarColumn;
import dev.mortar.processor.MortarEntity;
import dev.mortar.processor.MortarId;

@MortarEntity(table = "technicians", alias = "te")
final class TechnicianRecord {
    @MortarId
    @MortarColumn(name = "id", nullable = false)
    Long id;

    @MortarColumn(name = "display_name", nullable = false)
    String displayName;

    @MortarColumn(name = "region", nullable = false)
    String region;
}
