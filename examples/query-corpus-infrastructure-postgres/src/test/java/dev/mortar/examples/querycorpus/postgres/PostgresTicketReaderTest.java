package dev.mortar.examples.querycorpus.postgres;

import static dev.mortar.examples.querycorpus.postgres.QCustomerRecord.CUSTOMER_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTechnicianRecord.TECHNICIAN_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTicketRecord.TICKET_RECORD;
import static dev.mortar.examples.querycorpus.postgres.QTicketStatusRecord.TICKET_STATUS_RECORD;
import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.MortarDiagnosticCode;
import dev.mortar.core.QueryDiagnostics;
import dev.mortar.core.QuerySpec;
import dev.mortar.examples.querycorpus.application.TicketDetail;
import dev.mortar.examples.querycorpus.application.TicketHeader;
import dev.mortar.examples.querycorpus.application.TicketListRow;
import dev.mortar.examples.querycorpus.application.TicketSearchCriteria;
import dev.mortar.examples.querycorpus.application.TicketStatusOption;
import dev.mortar.examples.querycorpus.domain.TicketPriority;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.postgres.PostgresQueryRenderer;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class PostgresTicketReaderTest {
    private final PostgresQueryRenderer renderer = new PostgresQueryRenderer();

    @Test
    void generatedTicketHeaderLookupStaysAtInfrastructureBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetchOptional(anyTicketLookupQuery()))
            .thenReturn(Optional.of(new QTicketRecord.FindByIdRow(
                42L,
                "Router outage",
                "critical",
                LocalDate.of(2026, 6, 1)
            )));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);

        Optional<TicketHeader> header = reader.findHeader(42L);

        assertThat(header).contains(new TicketHeader(42L, "Router outage", "critical", LocalDate.of(2026, 6, 1)));
        verify(jdbcClient).fetchOptional(anyTicketLookupQuery());
    }

    @Test
    void generatedTicketHeaderLookupHasStableSql() {
        MortarBoundQuery<QTicketRecord.FindByIdRow> query = TICKET_RECORD.read(renderer)
            .findById(42L)
            .named("TicketReader.findHeader");

        assertThatSql(query)
            .hasSql("""
                select t.id, t.summary, t.priority, t.opened_on from tickets t where t.id = ?""")
            .hasParameters(42L)
            .hasParameterTypes(Long.class)
            .hasTables(TICKET_RECORD.table)
            .hasColumns(
                TICKET_RECORD.id,
                TICKET_RECORD.summary,
                TICKET_RECORD.priority,
                TICKET_RECORD.openedOn
            );
    }

    @Test
    void generatedStatusReferenceReadHasStableSql() {
        MortarBoundQuery<QTicketStatusRecord.FindAllRow> query = TICKET_STATUS_RECORD.read(renderer)
            .findAll()
            .named("TicketReader.listStatusOptions");

        assertThatSql(query)
            .hasSql("select ts.code, ts.name from ticket_statuses ts")
            .hasParameters()
            .hasParameterTypes()
            .hasTables(TICKET_STATUS_RECORD.table)
            .hasColumns(TICKET_STATUS_RECORD.code, TICKET_STATUS_RECORD.name);
    }

    @Test
    void statusOptionsUseGeneratedReadFacadeAtRuntimeBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        when(jdbcClient.fetch(anyStatusOptionsQuery()))
            .thenReturn(List.of(new QTicketStatusRecord.FindAllRow("open", "Open")));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);

        List<TicketStatusOption> result = reader.listStatusOptions();

        assertThat(result).containsExactly(new TicketStatusOption("open", "Open"));
        verify(jdbcClient).fetch(anyStatusOptionsQuery());
    }

    @Test
    void optionalFilterSearchUsesExplicitDslWithStableSqlAndMetadata() {
        TicketSearchCriteria criteria = new TicketSearchCriteria(
            "open",
            TicketPriority.CRITICAL,
            "north",
            "ada",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 2),
            true
        );
        QuerySpec query = new PostgresTicketReader(mock(MortarJdbcClient.class), renderer)
            .searchTicketsQuery(criteria, 0, 25);

        assertThatSql(renderer.render(query))
            .hasSql("""
                select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and t.priority = ? and te.region = ? and cu.name ilike ? and t.opened_on >= ? and t.opened_on <= ? and t.assigned_technician_id is not null order by t.opened_on desc, t.id asc limit ? offset ?""")
            .hasParameters(
                "open",
                "critical",
                "north",
                "%ada%",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 2),
                25,
                0
            )
            .hasParameterTypes(
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDate.class,
                LocalDate.class,
                Integer.class,
                Integer.class
            )
            .hasTables(TICKET_RECORD.table, CUSTOMER_RECORD.table, TECHNICIAN_RECORD.table, TICKET_STATUS_RECORD.table)
            .hasColumns(
                TICKET_RECORD.id,
                TICKET_STATUS_RECORD.name,
                TICKET_RECORD.priority,
                CUSTOMER_RECORD.name,
                TECHNICIAN_RECORD.displayName,
                TICKET_RECORD.openedOn,
                TICKET_RECORD.customer.innerJoin().leftColumn(),
                TICKET_RECORD.customer.innerJoin().rightColumn(),
                TICKET_RECORD.assignedTechnician.leftJoin().leftColumn(),
                TICKET_RECORD.assignedTechnician.leftJoin().rightColumn(),
                TICKET_RECORD.status.innerJoin().leftColumn(),
                TICKET_RECORD.status.innerJoin().rightColumn(),
                TECHNICIAN_RECORD.region
            )
            .hasJoins(
                TICKET_RECORD.customer.innerJoin(),
                TICKET_RECORD.assignedTechnician.leftJoin(),
                TICKET_RECORD.status.innerJoin()
            );
    }

    @Test
    void openTicketsForRegionUsesExplicitJoinProjectionAndOrderedPage() {
        QuerySpec query = new PostgresTicketReader(mock(MortarJdbcClient.class), renderer)
            .findOpenTicketsForRegionQuery("north", 1, 10);

        assertThatSql(renderer.render(query))
            .hasSql("""
                select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and te.region = ? order by t.opened_on desc, t.id asc limit ? offset ?""")
            .hasParameters("open", "north", 10, 10)
            .hasParameterTypes(String.class, String.class, Integer.class, Integer.class);
    }

    @Test
    void unassignedCriticalTicketsUseExplicitNullPredicateAndOrderedPage() {
        QuerySpec query = new PostgresTicketReader(mock(MortarJdbcClient.class), renderer)
            .findUnassignedCriticalTicketsQuery(0, 10);

        assertThatSql(renderer.render(query))
            .hasSql("""
                select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and t.priority = ? and t.assigned_technician_id is null order by t.opened_on desc, t.id asc limit ? offset ?""")
            .hasParameters("open", "critical", 10, 0)
            .hasParameterTypes(String.class, String.class, Integer.class, Integer.class);
    }

    @Test
    void unorderedPageRemainsDiagnosticEvidenceNotHiddenCoreEnforcement() {
        QuerySpec query = new PostgresTicketReader(mock(MortarJdbcClient.class), renderer)
            .unorderedPaginationCandidateQuery(0, 10);

        assertThat(QueryDiagnostics.analyze(query))
            .anySatisfy(diagnostic -> assertThat(diagnostic.code())
                .isEqualTo(MortarDiagnosticCode.UNSTABLE_PAGINATION));
    }

    @Test
    void adapterFetchesDslProjectionThroughJdbcRuntimeBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        TicketListRow row = new TicketListRow(
            42L,
            "Open",
            "critical",
            "Ada Manufacturing",
            "Grace Hopper",
            LocalDate.of(2026, 6, 1)
        );
        when(jdbcClient.fetch(any(QuerySpec.class), eq(TicketListRow.class))).thenReturn(List.of(row));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);

        List<TicketListRow> result = reader.findOpenTicketsForRegion("north", 1, 10);

        assertThat(result).containsExactly(row);
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(TicketListRow.class));
        assertThat(query.getValue().name()).contains("TicketReader.findOpenTicketsForRegion");
    }

    @Test
    void searchTicketsFetchesDslProjectionThroughJdbcRuntimeBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        TicketListRow row = ticketListRow();
        when(jdbcClient.fetch(any(QuerySpec.class), eq(TicketListRow.class))).thenReturn(List.of(row));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);
        TicketSearchCriteria criteria = new TicketSearchCriteria(
            "open",
            TicketPriority.CRITICAL,
            "north",
            "ada",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 2),
            true
        );

        List<TicketListRow> result = reader.searchTickets(criteria, 0, 25);

        assertThat(result).containsExactly(row);
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(TicketListRow.class));
        assertThat(query.getValue().name()).contains("TicketReader.searchTickets");
    }

    @Test
    void unassignedCriticalTicketsFetchesDslProjectionThroughJdbcRuntimeBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        TicketListRow row = ticketListRow();
        when(jdbcClient.fetch(any(QuerySpec.class), eq(TicketListRow.class))).thenReturn(List.of(row));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);

        List<TicketListRow> result = reader.findUnassignedCriticalTickets(0, 10);

        assertThat(result).containsExactly(row);
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(TicketListRow.class));
        assertThat(query.getValue().name()).contains("TicketReader.findUnassignedCriticalTickets");
    }

    @Test
    void detailFetchesDslProjectionThroughJdbcRuntimeBoundary() {
        MortarJdbcClient jdbcClient = mock(MortarJdbcClient.class);
        TicketDetail detail = new TicketDetail(
            42L,
            "Router outage",
            "Open",
            "Ada Manufacturing",
            "Grace Hopper",
            "north"
        );
        when(jdbcClient.fetch(any(QuerySpec.class), eq(TicketDetail.class))).thenReturn(List.of(detail));
        PostgresTicketReader reader = new PostgresTicketReader(jdbcClient, renderer);

        Optional<TicketDetail> result = reader.findDetail(42L);

        assertThat(result).contains(detail);
        ArgumentCaptor<QuerySpec> query = ArgumentCaptor.forClass(QuerySpec.class);
        verify(jdbcClient).fetch(query.capture(), eq(TicketDetail.class));
        assertThat(query.getValue().name()).contains("TicketReader.findDetail");
    }

    @Test
    void ticketDetailUsesExplicitJoinProjectionWithoutGeneratedTraversal() {
        QuerySpec query = new PostgresTicketReader(mock(MortarJdbcClient.class), renderer)
            .findDetailQuery(42L);

        assertThatSql(renderer.render(query))
            .hasSql("""
                select t.id, t.summary, ts.name, cu.name, te.display_name, te.region from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.id = ?""")
            .hasParameters(42L)
            .hasParameterTypes(Long.class);
    }

    @SuppressWarnings("unchecked")
    private MortarBoundQuery<QTicketRecord.FindByIdRow> anyTicketLookupQuery() {
        return any(MortarBoundQuery.class);
    }

    @SuppressWarnings("unchecked")
    private MortarBoundQuery<QTicketStatusRecord.FindAllRow> anyStatusOptionsQuery() {
        return any(MortarBoundQuery.class);
    }

    private static TicketListRow ticketListRow() {
        return new TicketListRow(
            42L,
            "Open",
            "critical",
            "Ada Manufacturing",
            "Grace Hopper",
            LocalDate.of(2026, 6, 1)
        );
    }
}
