use criterion::Criterion;
use std::hint::black_box;
use tree_sitter::Parser;

#[path = "../../tests/support/r20_lsp_benchmark.rs"]
mod r20_lsp_benchmark;

use r20_lsp_benchmark::{
    R20LspBenchmarkWorkspace, canonical_generated_read_document,
    large_document_with_canonical_call, local_metamodel_alias_document,
    local_read_namespace_alias_document, malformed_document, position_of,
    stale_source_map_document, state_with_document, unsupported_alias_document,
};

pub fn lsp_parser(c: &mut Criterion, group_name: &str) {
    let large_document = large_document_with_canonical_call(250);
    let mut group = c.benchmark_group(group_name);

    group.bench_function("canonical_generated_read", |bench| {
        bench.iter(|| parse_java(black_box(canonical_generated_read_document())));
    });
    group.bench_function("supported_metamodel_alias", |bench| {
        bench.iter(|| parse_java(black_box(local_metamodel_alias_document())));
    });
    group.bench_function("supported_read_namespace_alias", |bench| {
        bench.iter(|| parse_java(black_box(local_read_namespace_alias_document())));
    });
    group.bench_function("unsupported_alias_fail_closed", |bench| {
        bench.iter(|| parse_java(black_box(unsupported_alias_document())));
    });
    group.bench_function("large_document_canonical_generated_read", |bench| {
        bench.iter(|| parse_java(black_box(&large_document)));
    });

    group.finish();
}

pub fn lsp_feature_resolution(c: &mut Criterion, group_name: &str) {
    let mut group = c.benchmark_group(group_name);

    bench_hover_code_actions_definition(
        &mut group,
        "canonical_generated_read",
        R20LspBenchmarkWorkspace::fresh(),
        canonical_generated_read_document(),
        "QClient.CLIENT.read",
    );
    bench_hover_code_actions_definition(
        &mut group,
        "supported_metamodel_alias",
        R20LspBenchmarkWorkspace::fresh(),
        local_metamodel_alias_document(),
        "client.read",
    );
    bench_hover_code_actions_definition(
        &mut group,
        "supported_read_namespace_alias",
        R20LspBenchmarkWorkspace::fresh(),
        local_read_namespace_alias_document(),
        "read.findById",
    );
    bench_hover_code_actions_definition(
        &mut group,
        "unsupported_alias_fail_closed",
        R20LspBenchmarkWorkspace::fresh(),
        unsupported_alias_document(),
        "findById",
    );
    bench_hover_code_actions_definition(
        &mut group,
        "stale_source_map_fail_closed",
        R20LspBenchmarkWorkspace::stale_source_map(),
        stale_source_map_document(),
        "client.read",
    );
    bench_hover_code_actions_definition(
        &mut group,
        "missing_snapshot_fail_closed",
        R20LspBenchmarkWorkspace::without_snapshot(),
        canonical_generated_read_document(),
        "QClient.CLIENT.read",
    );

    group.finish();
}

pub fn lsp_diagnostics_and_full_sync(c: &mut Criterion, group_name: &str) {
    let mut group = c.benchmark_group(group_name);
    let large_document = large_document_with_canonical_call(250);

    bench_diagnostics(
        &mut group,
        "canonical_generated_read",
        R20LspBenchmarkWorkspace::fresh(),
        canonical_generated_read_document(),
    );
    bench_diagnostics(
        &mut group,
        "unsupported_alias_fail_closed",
        R20LspBenchmarkWorkspace::fresh(),
        unsupported_alias_document(),
    );
    bench_diagnostics(
        &mut group,
        "large_document_canonical_generated_read",
        R20LspBenchmarkWorkspace::fresh(),
        &large_document,
    );

    group.bench_function(
        "incremental_full_sync_success_unsupported_malformed_recover",
        |bench| {
            let workspace = R20LspBenchmarkWorkspace::fresh();
            let uri = workspace.uri("ClientUsage.java");
            bench.iter(|| {
                let mut state = mortar_lsp::LspState::new(workspace.root().to_path_buf());
                state.open_document(&uri, canonical_generated_read_document().to_string());
                let _ = state.document_diagnostics(&uri);
                state.change_document(&uri, unsupported_alias_document().to_string());
                let _ = state.document_diagnostics(&uri);
                state.change_document(&uri, malformed_document().to_string());
                let _ = state.document_diagnostics(&uri);
                state.change_document(&uri, canonical_generated_read_document().to_string());
                state.document_diagnostics(&uri)
            });
        },
    );

    group.finish();
}

fn parse_java(document: &str) -> bool {
    let mut parser = Parser::new();
    let language = tree_sitter_java::LANGUAGE;
    parser
        .set_language(&language.into())
        .expect("Java grammar should load");
    parser
        .parse(document, None)
        .map(|tree| black_box(tree.root_node().has_error()))
        .unwrap_or(true)
}

fn bench_hover_code_actions_definition(
    group: &mut criterion::BenchmarkGroup<'_, criterion::measurement::WallTime>,
    name: &str,
    workspace: R20LspBenchmarkWorkspace,
    document: &str,
    needle: &str,
) {
    let (state, uri) = state_with_document(&workspace, "ClientUsage.java", document);
    let position = position_of(document, needle);

    group.bench_function(name, |bench| {
        bench.iter(|| {
            let hover = black_box(state.hover(&uri, position));
            let actions = black_box(state.code_actions(&uri, position));
            let definition = black_box(state.definition(&uri, position));
            black_box((hover, actions, definition))
        });
    });
}

fn bench_diagnostics(
    group: &mut criterion::BenchmarkGroup<'_, criterion::measurement::WallTime>,
    name: &str,
    workspace: R20LspBenchmarkWorkspace,
    document: &str,
) {
    let (state, uri) = state_with_document(&workspace, "ClientUsage.java", document);
    group.bench_function(name, |bench| {
        bench.iter(|| black_box(state.document_diagnostics(&uri)));
    });
}
