# ADR-0006: R17 Query Family Decisions

Date: 2026-06-02

Status: Accepted

## Problem

R17 must decide whether broader query shapes justify generated API expansion
after R16 introduced fixed read facades. The decision must be based on a public
neutral fixture corpus, not abstract feature parity pressure or private
application migration.

The risk is that optional filters, joins, projections, stable pagination,
`count`, and `exists` can turn a small generated read surface into an overload
matrix, generated repository layer, or ORM-like abstraction.

## Fixture Evidence

R17 uses the public service-ticket corpus in:

- `examples/query-corpus-domain`;
- `examples/query-corpus-application`;
- `examples/query-corpus-infrastructure-postgres`.

The corpus has four annotated persistence entities: ticket, customer,
technician, and ticket status. It exercises three explicit relations:
ticket-to-customer, ticket-to-technician, and ticket-to-status. Domain and
application modules do not depend on Mortar APIs; generated `Q*`, `QuerySpec`,
`MortarBoundQuery<?>`, `MortarJdbcClient`, SQL assertions, and snapshots stay in
the PostgreSQL infrastructure module and tests.

The fixture proves that the existing R16 generated read facade is useful for
fixed lookup and reference-table reads, while the explicit DSL remains clearer
for optional filters, relation joins, read-model projections, and ordered pages.

## Decision

| Query family | R17 decision | Reason |
| --- | --- | --- |
| Fixed lookup | Generated as-is | Existing `read(renderer).findById(id)` is bounded, inspectable, and tested. |
| Reference `findAll` | Generated as-is | Existing `read(renderer).findAll()` fits small reference tables such as ticket statuses. |
| Optional filters | DSL-only | The corpus uses explicit skipped-filter semantics in adapter code; generated overloads would grow with filter combinations. |
| Multi-predicate reads | DSL-only | Fixed business predicates are domain-specific and readable as explicit Java. |
| Joins | DSL-only | Generated relation metadata supplies explicit join paths, but no generated traversal or aggregate loading is approved. |
| DTO/record projections | DSL-only | `projectRecord`/`projectDto` already keep row shape visible without adding generated projection helpers. |
| Stable pagination | DSL-only | Corpus page queries must call `orderBy(...)` before `page(...)`; hidden defaults are rejected. Core enforcement is deferred. |
| `count` | Deferred | A scalar visible-query/runtime contract would require an ADR-sized API decision. |
| `exists` | Deferred | Boolean/scalar execution would require a bounded query contract separate from row-loading reads. |
| Writes and batches | Rejected for R17 | R17 is a read-query evidence gate. |
| Generated repositories | Rejected | Repositories belong to application/infrastructure code, not generated Mortar API. |
| Self-executing query objects | Rejected | Execution remains adapter-owned through `MortarJdbcClient` or equivalent runtime adapters. |
| ORM behavior | Rejected | No lazy loading, identity map, aggregate loading, or implicit relation traversal. |

## Consequences

No product API expands in R17. The generated API budget from ADR-0005 remains
intact: generated read facades stay limited to fixed `findById` and `findAll`
query values.

R18 receives a reusable fixture for generator golden tests, metadata drift,
rename/delete failures, schema drift, and editor/source-map hardening. Any
future generated API for optional filters, scalar reads, pagination enforcement,
or projections must cite corpus evidence and open a new decision record before
implementation.

## Rejected Alternatives

- Generate optional-filter overloads: rejected because method count grows with
  nullable filter combinations.
- Generate a hybrid filter builder in R17: deferred because the fixture did not
  show repeated pain that the explicit DSL failed to handle.
- Generate relation traversal methods: rejected because traversal pressure
  leads toward implicit joins and aggregate loading.
- Add `count` or `exists` helpers as small conveniences: rejected for R17
  because scalar results reopen visible-query and runtime mapping contracts.
- Add pagination defaults: rejected because hidden ordering would obscure SQL
  and business ordering rules.
