mod support;

use lsp_types::{CodeActionOrCommand, DiagnosticSeverity, HoverContents, MarkedString};
use support::r20_lsp_benchmark::{
    R20LspBenchmarkWorkspace, canonical_generated_read_document,
    large_document_with_canonical_call, local_metamodel_alias_document,
    local_read_namespace_alias_document, malformed_document, position_of,
    stale_source_map_document, state_with_document, unsupported_alias_document,
};

const CLIENT_FIND_BY_ID_SQL: &str = "select c.id from clients c where c.id = ?";

#[test]
fn r20_lsp_benchmark_corpus_covers_success_and_fail_closed_scenarios() {
    assert_success(canonical_generated_read_document(), "QClient.CLIENT.read");
    assert_success(local_metamodel_alias_document(), "client.read");
    assert_success(local_read_namespace_alias_document(), "read.findById");
    assert_diagnostic(
        R20LspBenchmarkWorkspace::fresh(),
        unsupported_alias_document(),
        "findById",
        "mortar-alias-unsupported",
    );
    assert_diagnostic(
        R20LspBenchmarkWorkspace::stale_source_map(),
        stale_source_map_document(),
        "client.read",
        "mortar-source-map-stale",
    );
    assert_diagnostic(
        R20LspBenchmarkWorkspace::without_snapshot(),
        canonical_generated_read_document(),
        "QClient.CLIENT.read",
        "mortar-snapshot-missing",
    );
}

#[test]
fn r20_lsp_benchmark_corpus_covers_large_and_incremental_full_sync_behavior() {
    let large_document = large_document_with_canonical_call(250);
    assert_success(&large_document, "QClient.CLIENT.read");

    let workspace = R20LspBenchmarkWorkspace::fresh();
    let uri = workspace.uri("ClientUsage.java");
    let mut state = mortar_lsp::LspState::new(workspace.root().to_path_buf());
    state.open_document(&uri, canonical_generated_read_document().to_string());
    assert!(state.document_diagnostics(&uri).is_empty());

    state.change_document(&uri, unsupported_alias_document().to_string());
    assert_eq!(state.document_diagnostics(&uri).len(), 1);

    state.change_document(&uri, malformed_document().to_string());
    assert_eq!(state.document_diagnostics(&uri).len(), 1);

    state.change_document(&uri, canonical_generated_read_document().to_string());
    assert!(state.document_diagnostics(&uri).is_empty());
}

fn assert_success(document: &str, needle: &str) {
    let workspace = R20LspBenchmarkWorkspace::fresh();
    let (state, uri) = state_with_document(&workspace, "ClientUsage.java", document);
    let position = position_of(document, needle);

    let hover = state
        .hover(&uri, position)
        .expect("hover should not fail")
        .expect("hover should resolve");
    assert!(matches!(
        hover.contents,
        HoverContents::Scalar(MarkedString::String(ref value))
            if value.contains(CLIENT_FIND_BY_ID_SQL)
    ));

    let actions = state
        .code_actions(&uri, position)
        .expect("code actions should not fail");
    assert_eq!(actions.len(), 2);
    let titles = actions
        .iter()
        .map(action_title)
        .collect::<std::collections::BTreeSet<_>>();
    assert!(titles.contains("Copy generated SQL"));
    assert!(titles.contains("Run PostgreSQL EXPLAIN"));

    assert!(
        state
            .definition(&uri, position)
            .expect("definition should not fail")
            .is_some()
    );
    assert!(state.document_diagnostics(&uri).is_empty());
}

fn action_title(action: &CodeActionOrCommand) -> &str {
    match action {
        CodeActionOrCommand::CodeAction(action) => action.title.as_str(),
        CodeActionOrCommand::Command(command) => command.title.as_str(),
    }
}

fn assert_diagnostic(
    workspace: R20LspBenchmarkWorkspace,
    document: &str,
    needle: &str,
    expected_code: &str,
) {
    let (state, uri) = state_with_document(&workspace, "ClientUsage.java", document);
    let position = position_of(document, needle);

    assert!(
        state
            .hover(&uri, position)
            .expect("hover should not fail")
            .is_none()
    );

    let diagnostics = state.document_diagnostics(&uri);
    assert!(
        diagnostics.iter().any(|diagnostic| {
            diagnostic.severity == Some(DiagnosticSeverity::WARNING)
                && matches!(
                    diagnostic.code,
                    Some(lsp_types::NumberOrString::String(ref code)) if code == expected_code
                )
        }),
        "expected diagnostic code {expected_code}, got {diagnostics:?}"
    );
}
