# ADR-0008: R19 Java Call Resolution Boundary

Date: 2026-06-02

Status: Accepted

## Problem

R18.5 made source-map-backed editor behavior work for canonical generated
fixed-read calls:

```java
QClient.CLIENT.read(renderer).findById(id);
QClient.CLIENT.read(renderer).findAll();
CLIENT.read(renderer).findById(id);
```

That implementation intentionally used lightweight Java matching in the Rust
LSP. It does not understand arbitrary Java call semantics, helper-returned
receivers, cross-file aliases, field aliases, reassignment, or full type
binding. This was acceptable for R18, but it is too easy to overclaim before
R20 performance work or public demo material.

R19 needs to harden editor semantics without turning Mortar into a Java IDE,
changing runtime Java APIs, or weakening ADR-0007's source-map and freshness
contract.

## Decision

R19 will use a bounded hybrid:

- `mortar-source-map-v1` plus fresh `mortar-metadata-v1` remains authoritative
  for generated fixed-read query identity, snapshot keys, row types,
  parameters, generated metamodel types, and freshness.
- Rust LSP call recovery should move from token-only matching toward a local
  Java syntax tree for the active document.
- Syntax-aware resolution may broaden support only to explicit same-file local
  alias patterns with finite rules.
- Unsupported, ambiguous, stale, missing, or generated-looking-but-unresolved
  calls must fail closed: no hover SQL, no copy SQL, no EXPLAIN action, no
  definition target, and a diagnostic when the call looks like supported
  generated read syntax but cannot be trusted.
- R19 does not provide full Java semantic resolution, classpath-aware binding,
  helper-returned receiver support, arbitrary DSL call-site analysis, or IDE
  replacement behavior.

The preferred syntax parser for implementation evaluation is `tree-sitter-java`
through Rust tooling because it can keep the LSP in Rust and support local
syntax structure without a Java workspace model. R19 planning does not commit
product code to a specific parser crate version; implementation must prove the
choice through tests and can update this ADR if the parser strategy changes.

## Supported R19 Call-Resolution Direction

R19 may support:

- the R18 canonical direct receiver shapes;
- explicit static imports of generated constants already supported by R18;
- same-method or same-block local aliases assigned once to a generated
  metamodel constant;
- same-method or same-block local aliases assigned once to the result of
  `.read(renderer)` when the initializer itself resolves to a generated
  metamodel constant;
- stable shadowing and ambiguity rules that prefer failing closed over
  guessing.

R19 must reject or defer:

- helper methods that return generated metamodels or read namespaces;
- field aliases and cross-method aliases;
- cross-file data-flow;
- reassigned or conditionally assigned aliases;
- wildcard static imports as a success path;
- casts, reflection, dynamic dispatch, or type inference beyond local syntax
  structure;
- arbitrary Java call chains that only happen to end in `findById` or
  `findAll`;
- editor-side SQL rendering.

## Research Basis

- LSP 3.17 models hover, code action, definition, and diagnostics as separate
  position/range-based features. Servers may return no result when data cannot
  be resolved, while diagnostics are published independently.
- VS Code language-server guidance treats language analysis as a separate
  process because parsing and static analysis can be CPU and memory intensive.
- `tree-sitter-java` provides Java syntax parsing for the Tree-sitter parsing
  library and has Rust bindings, but it is syntax-oriented and does not provide
  Java type binding by itself.
- Java `JavaCompiler` and `com.sun.source.util.Trees` expose compiler and tree
  APIs that can provide deeper semantic information, but using them would add a
  Java process, classpath model, and workspace integration burden to the Rust
  LSP path.
- Eclipse JDT LS validates that full Java language tooling uses JDT, Maven,
  Gradle, diagnostics, completion, navigation, and annotation-processing
  support as a broad Java language server, which is larger than R19.
- JavaParser and JavaSymbolSolver validate AST plus symbol-solving options, but
  they would add Java-side dependency and classpath complexity that R19 does
  not need for bounded local alias hardening.

Primary sources:

- LSP 3.17 specification:
  https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
- VS Code language server extension guide:
  https://code.visualstudio.com/api/language-extensions/language-server-extension-guide
- VS Code API command registration:
  https://code.visualstudio.com/api/references/vscode-api
- Tree-sitter parser guide:
  https://tree-sitter.github.io/tree-sitter/using-parsers/
- `tree-sitter-java` Rust crate:
  https://docs.rs/tree-sitter-java/latest/tree_sitter_java/
- Java SE 21 `JavaCompiler`:
  https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/JavaCompiler.html
- Java SE 21 `Trees`:
  https://docs.oracle.com/en/java/javase/21/docs/api/jdk.compiler/com/sun/source/util/Trees.html
- Eclipse JDT Language Server:
  https://github.com/eclipse-jdtls/eclipse.jdt.ls
- Eclipse JDT `ASTParser`:
  https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html
- JavaParser:
  https://github.com/javaparser/javaparser

## Alternatives Considered

- Continue broadening the R18 lightweight scanner: rejected because alias,
  scope, shadowing, and ambiguity handling would turn token walking into an ad
  hoc parser with weaker correctness.
- Integrate `javac`, Eclipse JDT, or JavaParser symbol solving in R19:
  rejected because full semantic Java resolution requires classpath and
  workspace modeling that is disproportionate to the bounded generated-read
  hardening goal.
- Defer parser work and add lexical alias heuristics only: rejected because
  aliases require scope and initializer structure to avoid misleading editor
  output.
- Replace ADR-0007 source-map freshness with call-site inference: rejected
  because source-map freshness is the cross-tool trust boundary and must remain
  authoritative.

## Consequences

- R19 can improve confidence for realistic generated-read authoring patterns
  without claiming full Java call semantics.
- Some valid Java code will intentionally receive no Mortar editor result until
  a later phase proves deeper semantic tooling.
- Rust tooling remains editor infrastructure, not the default Java runtime path.
- VS Code remains a thin client over the shared LSP.
- Performance tuning, parser caching, and benchmark claims remain R20 scope.
