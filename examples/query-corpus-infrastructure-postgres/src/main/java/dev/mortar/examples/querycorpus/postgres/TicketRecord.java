package dev.mortar.examples.querycorpus.postgres;

import dev.mortar.processor.MortarColumn;
import dev.mortar.processor.MortarEntity;
import dev.mortar.processor.MortarId;
import dev.mortar.processor.MortarRelation;
import java.time.LocalDate;

@MortarEntity(table = "tickets", alias = "t")
final class TicketRecord {
    @MortarId
    @MortarColumn(name = "id", nullable = false)
    Long id;

    @MortarColumn(name = "summary", nullable = false)
    String summary;

    @MortarColumn(name = "priority", nullable = false)
    String priority;

    @MortarColumn(name = "opened_on", nullable = false)
    LocalDate openedOn;

    @MortarRelation(target = CustomerRecord.class, localColumn = "customer_id", nullable = false)
    CustomerRecord customer;

    @MortarRelation(target = TechnicianRecord.class, localColumn = "assigned_technician_id")
    TechnicianRecord assignedTechnician;

    @MortarRelation(target = TicketStatusRecord.class, localColumn = "status_code", targetColumn = "code", nullable = false)
    TicketStatusRecord status;
}
