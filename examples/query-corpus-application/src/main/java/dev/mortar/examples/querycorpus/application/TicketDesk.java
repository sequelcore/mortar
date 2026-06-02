package dev.mortar.examples.querycorpus.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class TicketDesk {
    private final TicketReader reader;

    public TicketDesk(TicketReader reader) {
        this.reader = Objects.requireNonNull(reader, "reader cannot be null");
    }

    public Optional<TicketHeader> findHeader(long ticketId) {
        return reader.findHeader(ticketId);
    }

    public Optional<TicketDetail> findDetail(long ticketId) {
        return reader.findDetail(ticketId);
    }

    public List<TicketStatusOption> listStatusOptions() {
        return reader.listStatusOptions();
    }

    public List<TicketListRow> search(TicketSearchCriteria criteria, int page, int size) {
        return reader.searchTickets(criteria, page, size);
    }

    public List<TicketListRow> openTicketsForRegion(String region, int page, int size) {
        return reader.findOpenTicketsForRegion(region, page, size);
    }

    public List<TicketListRow> unassignedCriticalTickets(int page, int size) {
        return reader.findUnassignedCriticalTickets(page, size);
    }
}
