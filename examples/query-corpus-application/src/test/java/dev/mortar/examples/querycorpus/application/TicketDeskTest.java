package dev.mortar.examples.querycorpus.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.examples.querycorpus.domain.TicketPriority;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class TicketDeskTest {
    @Test
    void delegatesTicketSearchToApplicationPortWithoutMortarTypes() {
        TicketReader reader = new StubTicketReader();
        TicketDesk desk = new TicketDesk(reader);
        TicketSearchCriteria criteria = new TicketSearchCriteria(
            "open",
            TicketPriority.CRITICAL,
            "north",
            "ada",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 2),
            true
        );

        List<TicketListRow> rows = desk.search(criteria, 0, 20);

        assertThat(rows).containsExactly(StubTicketReader.LIST_ROW);
    }

    @Test
    void delegatesAllTicketDeskFlowsToApplicationPort() {
        TicketDesk desk = new TicketDesk(new StubTicketReader());

        assertThat(desk.findHeader(42L)).contains(StubTicketReader.HEADER);
        assertThat(desk.findDetail(42L)).contains(StubTicketReader.DETAIL);
        assertThat(desk.listStatusOptions()).containsExactly(StubTicketReader.STATUS);
        assertThat(desk.openTicketsForRegion("north", 0, 10)).containsExactly(StubTicketReader.LIST_ROW);
        assertThat(desk.unassignedCriticalTickets(0, 10)).containsExactly(StubTicketReader.LIST_ROW);
    }

    @Test
    void searchCriteriaExposesOnlyPresentFilters() {
        TicketSearchCriteria criteria = new TicketSearchCriteria(
            " ",
            null,
            "north",
            "ada",
            null,
            LocalDate.of(2026, 6, 2),
            false
        );

        assertThat(criteria.optionalStatusCode()).isEmpty();
        assertThat(criteria.optionalPriority()).isEmpty();
        assertThat(criteria.optionalTechnicianRegion()).contains("north");
        assertThat(criteria.optionalCustomerNameContains()).contains("ada");
        assertThat(criteria.optionalOpenedFrom()).isEmpty();
        assertThat(criteria.optionalOpenedTo()).contains(LocalDate.of(2026, 6, 2));
        assertThat(criteria.optionalAssignedOnly()).contains(false);
    }

    private static final class StubTicketReader implements TicketReader {
        private static final TicketHeader HEADER = new TicketHeader(
            42L,
            "Router outage",
            "critical",
            LocalDate.of(2026, 6, 1)
        );
        private static final TicketDetail DETAIL = new TicketDetail(
            42L,
            "Router outage",
            "Open",
            "Ada Manufacturing",
            "Grace Hopper",
            "north"
        );
        private static final TicketStatusOption STATUS = new TicketStatusOption("open", "Open");
        private static final TicketListRow LIST_ROW = new TicketListRow(
            42L,
            "Open",
            "critical",
            "Ada Manufacturing",
            "Grace Hopper",
            LocalDate.of(2026, 6, 1)
        );

        @Override
        public Optional<TicketHeader> findHeader(long ticketId) {
            return Optional.of(HEADER);
        }

        @Override
        public Optional<TicketDetail> findDetail(long ticketId) {
            return Optional.of(DETAIL);
        }

        @Override
        public List<TicketStatusOption> listStatusOptions() {
            return List.of(STATUS);
        }

        @Override
        public List<TicketListRow> searchTickets(TicketSearchCriteria criteria, int page, int size) {
            return List.of(LIST_ROW);
        }

        @Override
        public List<TicketListRow> findOpenTicketsForRegion(String region, int page, int size) {
            return List.of(LIST_ROW);
        }

        @Override
        public List<TicketListRow> findUnassignedCriticalTickets(int page, int size) {
            return List.of(LIST_ROW);
        }
    }
}
