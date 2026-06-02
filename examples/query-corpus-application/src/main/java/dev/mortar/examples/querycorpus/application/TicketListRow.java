package dev.mortar.examples.querycorpus.application;

import java.time.LocalDate;

public record TicketListRow(
    Long ticketId,
    String statusName,
    String priority,
    String customerName,
    String technicianName,
    LocalDate openedOn
) {
}
