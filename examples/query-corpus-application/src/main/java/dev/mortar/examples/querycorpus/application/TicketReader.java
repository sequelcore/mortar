package dev.mortar.examples.querycorpus.application;

import java.util.List;
import java.util.Optional;

public interface TicketReader {
    Optional<TicketHeader> findHeader(long ticketId);

    Optional<TicketDetail> findDetail(long ticketId);

    List<TicketStatusOption> listStatusOptions();

    List<TicketListRow> searchTickets(TicketSearchCriteria criteria, int page, int size);

    List<TicketListRow> findOpenTicketsForRegion(String region, int page, int size);

    List<TicketListRow> findUnassignedCriticalTickets(int page, int size);
}
