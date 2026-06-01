# ADR-0002: Apache-2.0 License For Public Distribution

Date: 2026-05-31

## Status

Accepted

## Context

Mortar is intended to be a public Java + Rust developer tool used inside commercial Spring Boot applications and open-source projects.

The project needs a permissive license that is familiar to enterprise Java teams, supports broad adoption, and includes an explicit patent grant.

## Decision

Mortar will use the Apache License, Version 2.0.

Java artifacts, Rust crates, documentation, and examples are distributed under Apache-2.0 unless a file explicitly says otherwise.

## Consequences

- Commercial and open-source users can adopt Mortar with a familiar permissive license.
- Contributors and users receive the Apache-2.0 patent grant.
- Release metadata for Maven and Cargo must declare `Apache-2.0`.
- Third-party notices must be maintained when dependencies or bundled assets require attribution.
