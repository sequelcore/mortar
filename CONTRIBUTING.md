# Contributing To Mortar

Contributions are welcome when they keep Mortar small, explicit, tested, and
transparent.

## Development Requirements

- Java 21.
- Rust stable.
- Docker for PostgreSQL integration tests once Testcontainers slices begin.
- Use the Gradle wrapper, not a system Gradle requirement.

## Quality Gates

Run these before opening a pull request:

```bash
./gradlew check
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

On Windows:

```bat
gradlew.bat check
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

## Code Standards

- No wildcard imports.
- No dead code.
- No framework dependencies in `java/core`.
- No hidden SQL behavior.
- No performance claims without benchmark evidence.
- Public API changes require docs and migration notes.
- Architecture changes require an ADR in `docs/adr`.

## Roadmap Rule

`docs/roadmap.md` is canonical. If a change completes, replaces, splits, or defers a roadmap slice, update the roadmap in the same pull request with verification evidence.

## Commit Format

Use:

```text
type(scope): description
```

Allowed types:

- `feat`
- `fix`
- `refactor`
- `chore`
- `docs`
- `test`

Examples:

```text
feat(core): add grouped predicates
docs(roadmap): mark R0 foundation complete
test(postgres): add join rendering snapshots
```

## Pull Request Checklist

- Tests or verification gates were run.
- Roadmap was updated if a slice changed.
- ADR was added for architecture changes.
- Public docs were updated for user-facing behavior.
- No unrelated files were changed.
