# Security Policy

## Supported Versions

Mortar is pre-1.0. Until the first stable release, only the current `main` branch receives security fixes.

## Reporting A Vulnerability

Do not open a public issue for vulnerabilities.

Report privately to the project maintainer with:

- affected version or commit;
- reproduction steps;
- impact assessment;
- whether credentials, SQL data, or generated metadata can leak;
- suggested fix, if known.

## Security Principles

Mortar must:

- bind parameters through prepared statements;
- never log parameter values by default in production;
- provide redaction hooks for SQL diagnostics;
- treat raw SQL escape hatches as explicitly unsafe APIs;
- avoid generated code that bypasses Java access/type checks;
- avoid requiring Rust tooling in the Spring runtime hot path.

## Disclosure

Confirmed vulnerabilities should receive:

- a patched release or documented mitigation;
- a changelog entry;
- migration guidance when public APIs change;
- a CVE request when severity and distribution justify it.
