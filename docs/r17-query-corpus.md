# R17 Query Corpus Fixture

R17 uses a public service-ticket mini-domain to evaluate broader query shapes
without migrating or disguising private application code.

## Fixture Shape

Modules:

- `examples/query-corpus-domain`: domain vocabulary, including
  `TicketPriority`.
- `examples/query-corpus-application`: Mortar-free ports, criteria, DTO records,
  and `TicketDesk`.
- `examples/query-corpus-infrastructure-postgres`: annotated persistence
  records, generated `Q*` metadata, PostgreSQL adapter queries, SQL drift tests,
  and snapshot inventory.

Annotated entities:

- `TicketRecord`: ticket summary, priority, opened date, and relation metadata.
- `CustomerRecord`: customer reference data.
- `TechnicianRecord`: technician display name and region.
- `TicketStatusRecord`: reference table for status options.

Relations:

- `TicketRecord.customer`: inner join to `CustomerRecord`.
- `TicketRecord.assignedTechnician`: left join to `TechnicianRecord`.
- `TicketRecord.status`: inner join to `TicketStatusRecord` by status code.

The domain is neutral because service-ticket workflows are common operational
examples and do not imply a private product domain. The corpus is small enough
for fast CI but has enough shape for R18 incremental compilation, metadata
drift, rename/delete, schema drift, and editor-source-map fixtures.

## Query Inventory

| Snapshot key | Query name | Family | Decision |
| --- | --- | --- | --- |
| `r17.ticket.header-by-id` | `TicketReader.findHeader` | Fixed lookup | Existing generated read facade |
| `r17.ticket.status-options` | `TicketReader.listStatusOptions` | Reference `findAll` | Existing generated read facade |
| `r17.ticket.search` | `TicketReader.searchTickets` | Optional filters, joins, projection, page | DSL-only |
| `r17.ticket.open-region-page` | `TicketReader.findOpenTicketsForRegion` | Multi-predicate region page | DSL-only |
| `r17.ticket.unassigned-critical-page` | `TicketReader.findUnassignedCriticalTickets` | Multi-predicate null filter page | DSL-only |
| `r17.ticket.detail` | `TicketReader.findDetail` | Detail projection with joins | DSL-only |

Snapshot fixture file:

- `examples/query-corpus-infrastructure-postgres/src/test/resources/r17-query-corpus/mortar.sql.snap.json`

## Stable SQL Expectations

`TicketReader.findHeader`:

```sql
select t.id, t.summary, t.priority, t.opened_on from tickets t where t.id = ?
```

`TicketReader.listStatusOptions`:

```sql
select ts.code, ts.name from ticket_statuses ts
```

`TicketReader.searchTickets`:

```sql
select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and t.priority = ? and te.region = ? and cu.name ilike ? and t.opened_on >= ? and t.opened_on <= ? and t.assigned_technician_id is not null order by t.opened_on desc, t.id asc limit ? offset ?
```

`TicketReader.findOpenTicketsForRegion`:

```sql
select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and te.region = ? order by t.opened_on desc, t.id asc limit ? offset ?
```

`TicketReader.findUnassignedCriticalTickets`:

```sql
select t.id, ts.name, t.priority, cu.name, te.display_name, t.opened_on from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.status_code = ? and t.priority = ? and t.assigned_technician_id is null order by t.opened_on desc, t.id asc limit ? offset ?
```

`TicketReader.findDetail`:

```sql
select t.id, t.summary, ts.name, cu.name, te.display_name, te.region from tickets t inner join customers cu on t.customer_id = cu.id left join technicians te on t.assigned_technician_id = te.id inner join ticket_statuses ts on t.status_code = ts.code where t.id = ?
```

## Metadata Expectations

Canonical SQL drift tests assert:

- SQL text;
- parameter values;
- parameter Java types;
- touched tables;
- touched columns, including relation local and target columns;
- explicit join list and join type;
- query names for generated bound reads and DSL query specs.

The optional-filter search uses the following parameter sequence:

1. `open` as `String`;
2. `critical` as `String`;
3. `north` as `String`;
4. `%ada%` as `String`;
5. `2026-06-01` as `LocalDate`;
6. `2026-06-02` as `LocalDate`;
7. page size as `Integer`;
8. page offset as `Integer`.

## R18 Handoff

R18 should use this corpus for:

- generator golden tests for `findById`, `findAll`, relation metadata, and
  snapshot keys;
- Gradle incremental compilation across domain, application, and infrastructure
  modules;
- rename failure cases for `TicketRecord.summary`,
  `TechnicianRecord.displayName`, and `TicketStatusRecord.code`;
- delete failure cases for `TicketRecord.customer`,
  `TicketRecord.assignedTechnician`, and `TicketRecord.status`;
- schema drift cases for `tickets.status_code`, `tickets.customer_id`,
  `tickets.assigned_technician_id`, and `technicians.region`;
- stale metadata diagnostics when generated `Q*` sources do not match the
  annotated persistence records;
- editor hover/navigation over generated read calls and explicit DSL source
  locations;
- future source-map needs for relation local columns and projection columns.

`count` and `exists` are intentionally not implemented in the fixture adapter.
The use cases are known: a search screen may need a total count, and a business
validator may need an existence check. R17 defers both because scalar query
results require a separate visible-query/runtime contract decision.
