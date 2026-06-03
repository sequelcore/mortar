use criterion::{Criterion, criterion_group, criterion_main};

#[path = "support/lsp_benchmark_suite.rs"]
mod lsp_benchmark_suite;

fn r20_lsp_parser(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_parser(c, "r20_lsp_parser");
}

fn r20_lsp_editor_features(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_feature_resolution(c, "r20_lsp_editor_features");
}

fn r20_lsp_diagnostics_and_full_sync(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_diagnostics_and_full_sync(c, "r20_lsp_diagnostics_full_sync");
}

criterion_group!(
    name = benches;
    config = Criterion::default();
    targets =
        r20_lsp_parser,
        r20_lsp_editor_features,
        r20_lsp_diagnostics_and_full_sync
);
criterion_main!(benches);
