package dev.mortar.examples.querycorpus.application;

public record TicketDetail(
    Long ticketId,
    String summary,
    String statusName,
    String customerName,
    String technicianName,
    String technicianRegion
) {
}
