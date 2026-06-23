# Changelog

All notable changes to Mortar will be documented in this file.

Mortar follows semantic versioning after the first public release. Before `1.0.0`, breaking changes may occur, but they must still be documented.

## Unreleased

## 0.1.0-alpha.2 - 2026-06-23

- Added a VS Code package contract check so VSIX builds fail if source files,
  tests, dependency folders, build caches, or oversized bundles enter the
  extension package.
- Expanded the README with install, tooling, VS Code configuration, generated
  read facade, SQL assertion, and first-user path examples.
- Documented the planned public Sequel backend demo path as a separate
  ecosystem showcase.
- Made JPA entity discovery explicit in the Mortar processor. `@MortarEntity`
  models are processed by default; `jakarta.persistence.Entity` models are
  processed only when `-Amortar.jpaDiscovery=true` is configured.
- Added generated fixed-read helper support for `java.util.UUID` identifiers.
- Documented incremental Spring Data JPA adoption guidance, including when to
  use dedicated Mortar row models and when to opt in to JPA discovery.

## 0.1.0-alpha.1 - 2026-06-04

- Created Java + Rust monorepo scaffold.
- Added core query model foundation.
- Added PostgreSQL renderer foundation.
- Added JDBC runtime foundation.
- Added Spring Boot starter foundation.
- Added testkit foundation.
- Added Rust compiler and CLI foundation.
- Added LSP, VS Code, and IntelliJ editor transparency tooling.
- Added runnable Spring Boot PostgreSQL example and public guides.
- Added Maven Central publishing configuration for public Java modules.
- Added CI release-readiness dry-run checks for Java Maven local publishing,
  Rust package inspection, the compiler crate publish dry-run, and VS Code VSIX
  packaging.
- Added guarded manual release automation for Maven Central, crates.io, and
  VS Code Marketplace pre-release publication.
- Published Java artifacts to Maven Central as `0.1.0-alpha.1`.
- Published Rust crates to crates.io as `sequel-mortar-compiler`,
  `sequel-mortar-cli`, and `sequel-mortar-lsp` version `0.1.0`.
- Published the VS Code extension to the Marketplace as
  `sequelcore.mortar-vscode` version `0.1.0` pre-release.
- Added dependency-update and CodeQL security automation for the public
  repository.
- Added canonical roadmap and public governance docs.
- Added R15 public API readiness hardening for generated read executors, Spring
  Boot starter diagnostics, PostgreSQL syntax evidence, processor diagnostics,
  usage guidance, additional examples, benchmark matrix breadth, and pre-1.0
  compatibility policy.
