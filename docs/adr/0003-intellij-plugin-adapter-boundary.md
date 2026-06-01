# ADR-0003: IntelliJ Plugin Adapter Boundary

Date: 2026-06-01

## Status

Accepted

## Context

Mortar is an editor-transparent Java query tool. VS Code is the first editor
workflow because it exercises the editor-neutral LSP path early, but Mortar is
public and should support IntelliJ after that foundation is useful.

The IntelliJ Platform Gradle Plugin is the official build path for JetBrains IDE
plugins. Adding it introduces a build-time dependency and IDE-specific APIs.

## Decision

Mortar will keep IntelliJ integration in `editors/intellij`.

The IntelliJ plugin may depend on IntelliJ Platform APIs and the bundled Java
plugin. Core Mortar modules must not depend on IntelliJ APIs. Shared query
inspection behavior should remain in Rust tooling or Java core contracts unless
an IDE-specific adapter is required.

## Consequences

- IntelliJ support can evolve without coupling `java/core` to IDE APIs.
- VS Code remains the primary user workflow while IntelliJ is built as a
  secondary public integration.
- Build verification includes IntelliJ plugin packaging once the project is
  scaffolded.
