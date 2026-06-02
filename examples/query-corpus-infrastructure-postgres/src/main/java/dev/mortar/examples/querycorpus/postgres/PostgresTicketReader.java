package dev.mortar.examples.querycorpus.postgres;

import static dev.mortar.examples.querycorpus.postgres.QCustomerRecord.CUSTOMER_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTechnicianRecord.TECHNICIAN_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTicketRecord.TICKET_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTicketStatusRecord.TICKET_STATUS_RECORD;

import dev.mortar.core.MortarDb;
import dev.mortar.core.MortarPage;
import dev.mortar.core.Operator;
import dev.mortar.core.Predicate;
import dev.mortar.core.Projection;
import dev.mortar.core.QueryBuilder;
import dev.mortar.core.QueryRenderer;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RelationRef;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.examples.querycorpus.application.TicketDetail;
import dev.mortar.examples.querycorpus.application.TicketHeader;
import dev.mortar.examples.querycorpus.application.TicketListRow;
import dev.mortar.examples.querycorpus.application.TicketReader;
import dev.mortar.examples.querycorpus.application.TicketSearchCriteria;
import dev.mortar.examples.querycorpus.application.TicketStatusOption;
import dev.mortar.jdbc.MortarJdbcClient;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class PostgresTicketReader implements TicketReader {
    private static final String OPEN_STATUS = "open";
    private static final String CRITICAL_PRIORITY = "critical";

    private final MortarJdbcClient jdbcClient;
    private final QueryRenderer renderer;
    private final MortarDb db;

    public PostgresTicketReader(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
        this(jdbcClient, renderer, new SimpleMortarDb());
    }

    PostgresTicketReader(MortarJdbcClient jdbcClient, QueryRenderer renderer, MortarDb db) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.renderer = Objects.requireNonNull(renderer, "renderer cannot be null");
        this.db = Objects.requireNonNull(db, "db cannot be null");
    }

    @Override
    public Optional<TicketHeader> findHeader(long ticketId) {
        return jdbcClient.fetchOptional(
                TICKET_RECORD.read(renderer)
                    .findById(ticketId)
                    .named("TicketReader.findHeader")
            )
            .map(row -> new TicketHeader(row.id(), row.summary(), row.priority(), row.openedOn()));
    }

    @Override
    public Optional<TicketDetail> findDetail(long ticketId) {
        return jdbcClient.fetch(findDetailQuery(ticketId), TicketDetail.class)
            .stream()
            .findFirst();
    }

    @Override
    public List<TicketStatusOption> listStatusOptions() {
        return jdbcClient.fetch(
                TICKET_STATUS_RECORD.read(renderer)
                    .findAll()
                    .named("TicketReader.listStatusOptions")
            )
            .stream()
            .map(row -> new TicketStatusOption(row.code(), row.name()))
            .toList();
    }

    @Override
    public List<TicketListRow> searchTickets(TicketSearchCriteria criteria, int page, int size) {
        return jdbcClient.fetch(searchTicketsQuery(criteria, page, size), TicketListRow.class);
    }

    @Override
    public List<TicketListRow> findOpenTicketsForRegion(String region, int page, int size) {
        return jdbcClient.fetch(findOpenTicketsForRegionQuery(region, page, size), TicketListRow.class);
    }

    @Override
    public List<TicketListRow> findUnassignedCriticalTickets(int page, int size) {
        return jdbcClient.fetch(findUnassignedCriticalTicketsQuery(page, size), TicketListRow.class);
    }

    QuerySpec searchTicketsQuery(TicketSearchCriteria criteria, int page, int size) {
        Objects.requireNonNull(criteria, "criteria cannot be null");
        QueryBuilder<QTicketRecord> query = ticketListQuery("TicketReader.searchTickets");

        criteria.optionalStatusCode().ifPresent(statusCode -> query.where(ticket -> relationEquals(ticket.status, statusCode)));
        criteria.optionalPriority().ifPresent(priority -> query.where(ticket -> ticket.priority.eq(priority.code())));
        criteria.optionalTechnicianRegion().ifPresent(region -> query.where(TECHNICIAN_RECORD.region.eq(region)));
        criteria.optionalCustomerNameContains().ifPresent(name -> query.where(CUSTOMER_RECORD.name.containsIgnoreCase(name)));
        criteria.optionalOpenedFrom().ifPresent(openedFrom -> query.where(ticket -> ticket.openedOn.gte(openedFrom)));
        criteria.optionalOpenedTo().ifPresent(openedTo -> query.where(ticket -> ticket.openedOn.lte(openedTo)));
        criteria.optionalAssignedOnly().ifPresent(assignedOnly -> {
            if (assignedOnly) {
                query.where(ticket -> ticket.assignedTechnician.localColumn().isNotNull());
            } else {
                query.where(ticket -> ticket.assignedTechnician.localColumn().isNull());
            }
        });

        return query
            .orderBy(ticket -> ticket.openedOn.desc())
            .orderBy(ticket -> ticket.id.asc())
            .page(MortarPage.of(page, size))
            .build();
    }

    QuerySpec findOpenTicketsForRegionQuery(String region, int page, int size) {
        Objects.requireNonNull(region, "region cannot be null");
        if (region.isBlank()) {
            throw new IllegalArgumentException("region cannot be blank");
        }

        return ticketListQuery("TicketReader.findOpenTicketsForRegion")
            .where(ticket -> relationEquals(ticket.status, OPEN_STATUS))
            .where(TECHNICIAN_RECORD.region.eq(region))
            .orderBy(ticket -> ticket.openedOn.desc())
            .orderBy(ticket -> ticket.id.asc())
            .page(MortarPage.of(page, size))
            .build();
    }

    QuerySpec findUnassignedCriticalTicketsQuery(int page, int size) {
        return ticketListQuery("TicketReader.findUnassignedCriticalTickets")
            .where(ticket -> relationEquals(ticket.status, OPEN_STATUS))
            .where(ticket -> ticket.priority.eq(CRITICAL_PRIORITY))
            .where(ticket -> ticket.assignedTechnician.localColumn().isNull())
            .orderBy(ticket -> ticket.openedOn.desc())
            .orderBy(ticket -> ticket.id.asc())
            .page(MortarPage.of(page, size))
            .build();
    }

    QuerySpec findDetailQuery(long ticketId) {
        return db.from(TICKET_RECORD)
            .project(Projection.record(TicketDetail.class, List.of(
                TICKET_RECORD.id,
                TICKET_RECORD.summary,
                TICKET_STATUS_RECORD.name,
                CUSTOMER_RECORD.name,
                TECHNICIAN_RECORD.displayName,
                TECHNICIAN_RECORD.region
            )))
            .innerJoin(ticket -> ticket.customer)
            .leftJoin(ticket -> ticket.assignedTechnician)
            .innerJoin(ticket -> ticket.status)
            .where(ticket -> ticket.id.eq(ticketId))
            .named("TicketReader.findDetail")
            .build();
    }

    QuerySpec unorderedPaginationCandidateQuery(int page, int size) {
        return ticketListQuery("TicketReader.unorderedPaginationCandidate")
            .page(MortarPage.of(page, size))
            .build();
    }

    private QueryBuilder<QTicketRecord> ticketListQuery(String queryName) {
        return db.from(TICKET_RECORD)
            .project(Projection.record(TicketListRow.class, List.of(
                TICKET_RECORD.id,
                TICKET_STATUS_RECORD.name,
                TICKET_RECORD.priority,
                CUSTOMER_RECORD.name,
                TECHNICIAN_RECORD.displayName,
                TICKET_RECORD.openedOn
            )))
            .innerJoin(ticket -> ticket.customer)
            .leftJoin(ticket -> ticket.assignedTechnician)
            .innerJoin(ticket -> ticket.status)
            .named(queryName);
    }

    private static Predicate relationEquals(RelationRef relation, Object value) {
        return Predicate.binary(relation.localColumn(), Operator.EQUALS, value);
    }
}
