package dev.mortar.examples.querycorpus.application;

import java.time.LocalDate;

public record TicketHeader(Long ticketId, String summary, String priority, LocalDate openedOn) {
}
