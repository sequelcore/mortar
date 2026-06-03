use criterion::{Criterion, criterion_group, criterion_main};

#[path = "support/lsp_benchmark_suite.rs"]
mod lsp_benchmark_suite;

fn r23_lsp_parser(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_parser(c, "r23_lsp_parser");
}

fn r23_lsp_feature_resolution(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_feature_resolution(c, "r23_lsp_feature_resolution");
}

fn r23_lsp_diagnostics_and_full_sync(c: &mut Criterion) {
    lsp_benchmark_suite::lsp_diagnostics_and_full_sync(c, "r23_lsp_diagnostics_full_sync");
}

criterion_group!(
    name = benches;
    config = Criterion::default();
    targets =
        r23_lsp_parser,
        r23_lsp_feature_resolution,
        r23_lsp_diagnostics_and_full_sync
);
criterion_main!(benches);
