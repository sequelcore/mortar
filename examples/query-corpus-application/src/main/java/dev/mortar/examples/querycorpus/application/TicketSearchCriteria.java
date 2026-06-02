package dev.mortar.examples.querycorpus.application;

import dev.mortar.examples.querycorpus.domain.TicketPriority;
import java.time.LocalDate;
import java.util.Optional;

public record TicketSearchCriteria(
    String statusCode,
    TicketPriority priority,
    String technicianRegion,
    String customerNameContains,
    LocalDate openedFrom,
    LocalDate openedTo,
    Boolean assignedOnly
) {
    public Optional<String> optionalStatusCode() {
        return nonBlank(statusCode);
    }

    public Optional<TicketPriority> optionalPriority() {
        return Optional.ofNullable(priority);
    }

    public Optional<String> optionalTechnicianRegion() {
        return nonBlank(technicianRegion);
    }

    public Optional<String> optionalCustomerNameContains() {
        return nonBlank(customerNameContains);
    }

    public Optional<LocalDate> optionalOpenedFrom() {
        return Optional.ofNullable(openedFrom);
    }

    public Optional<LocalDate> optionalOpenedTo() {
        return Optional.ofNullable(openedTo);
    }

    public Optional<Boolean> optionalAssignedOnly() {
        return Optional.ofNullable(assignedOnly);
    }

    private static Optional<String> nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
