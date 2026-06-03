package dev.mortar.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dev.mortar.postgres.PostgresQueryRenderer;

import org.junit.jupiter.api.Test;

final class PostgresExecutionBenchmarkTest {
    private static final Map<String, String> R20_3_BASELINE_MATRIX = Map.ofEntries(
        Map.entry("ordinary-jdbc", "plainJdbcFetch"),
        Map.entry("reusable-prepared-jdbc", "plainJdbcReusableStatementFetch"),
        Map.entry("ordinary-jdbc-find-by-id", "plainJdbcFindByIdFetch"),
        Map.entry("reusable-prepared-jdbc-find-by-id", "plainJdbcReusableFindByIdFetch"),
        Map.entry("tuned-pgjdbc-reusable-jdbc", "plainJdbcTunedReusableFindByIdFetch"),
        Map.entry("mortar-render-per-call", "mortarJdbcFetch"),
        Map.entry("mortar-pre-rendered-sql", "mortarPreRenderedJdbcFetch"),
        Map.entry("mortar-processor-generated-executor", "mortarProcessorGeneratedFindByIdFetch"),
        Map.entry("mortar-prepared-processor-generated-executor", "mortarPreparedProcessorGeneratedFindByIdFetch"),
        Map.entry("mortar-tuned-processor-generated-executor", "mortarTunedProcessorGeneratedFindByIdFetch"),
        Map.entry("jooq-reference", "jooqFetch"),
        Map.entry("querydsl-sql-reference", "querydslFetch")
    );
    private static final Map<String, String> R20_4_GENERATED_FIXED_READ_MATRIX = Map.ofEntries(
        Map.entry("ordinary-jdbc-find-by-id", "plainJdbcFindByIdFetch"),
        Map.entry("reusable-prepared-jdbc-find-by-id", "plainJdbcReusableFindByIdFetch"),
        Map.entry("tuned-pgjdbc-reusable-jdbc-find-by-id", "plainJdbcTunedReusableFindByIdFetch"),
        Map.entry("mortar-processor-generated-find-by-id", "mortarProcessorGeneratedFindByIdFetch"),
        Map.entry(
            "mortar-prepared-processor-generated-find-by-id",
            "mortarPreparedProcessorGeneratedFindByIdFetch"
        ),
        Map.entry("mortar-tuned-processor-generated-find-by-id", "mortarTunedProcessorGeneratedFindByIdFetch")
    );
    private static final String R20_4_GENERATED_FIXED_READ_INCLUDE_REGEX =
        "PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|"
            + "plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|"
            + "mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$";

    @Test
    void fixtureDefinesDeterministicIndexedLookupDataset() {
        assertThat(PostgresExecutionBenchmark.DATASET_SIZE).isEqualTo(1_000);
        assertThat(PostgresExecutionBenchmark.QUERY_CLIENT_ID).isEqualTo(777L);
        assertThat(PostgresExecutionBenchmark.CREATE_SCHEMA_SQL)
            .contains("id bigint primary key")
            .contains("route_id bigint not null")
            .contains("create index clients_active_id_idx");
        assertThat(PostgresExecutionBenchmark.seedClientName(PostgresExecutionBenchmark.QUERY_CLIENT_ID))
            .isEqualTo("client-0777");
    }

    @Test
    void exposesEquivalentExecutionBenchmarkMethods() {
        Set<String> methodNames = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(methodNames).contains(
            "plainJdbcFetch",
            "plainJdbcFetchOptional",
            "plainJdbcReusableStatementFetch",
            "plainJdbcReusableStatementFetchOptional",
            "mortarJdbcFetch",
            "mortarJdbcFetchOptional",
            "mortarPreRenderedJdbcFetch",
            "mortarPreRenderedJdbcFetchOptional",
            "mortarGeneratedJdbcFetch",
            "mortarGeneratedJdbcFetchOptional",
            "mortarPreparedGeneratedJdbcFetch",
            "mortarPreparedGeneratedJdbcFetchOptional",
            "mortarProcessorGeneratedFindByIdFetch",
            "mortarProcessorGeneratedFindByIdFetchOptional",
            "mortarPreparedProcessorGeneratedFindByIdFetch",
            "mortarPreparedProcessorGeneratedFindByIdFetchOptional",
            "plainJdbcFindByIdFetch",
            "plainJdbcFindByIdFetchOptional",
            "plainJdbcReusableFindByIdFetch",
            "plainJdbcReusableFindByIdFetchOptional",
            "plainJdbcJoinPageFetch",
            "mortarJoinPageFetch",
            "plainJdbcUpdateBatch",
            "mortarUpdateBatch",
            "plainJdbcTunedReusableFindByIdFetch",
            "mortarTunedProcessorGeneratedFindByIdFetch",
            "jooqFetch",
            "jooqFetchOptional",
            "querydslFetch",
            "querydslFetchOptional"
        );
    }

    @Test
    void r20BaselineMatrixUsesOnlyLivePostgresFixedReadScenarioNames() {
        Set<String> methodNames = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(R20_3_BASELINE_MATRIX)
            .containsKeys(
                "ordinary-jdbc",
                "reusable-prepared-jdbc",
                "tuned-pgjdbc-reusable-jdbc",
                "mortar-render-per-call",
                "mortar-pre-rendered-sql",
                "mortar-processor-generated-executor",
                "mortar-prepared-processor-generated-executor",
                "jooq-reference",
                "querydsl-sql-reference"
            );
        assertThat(methodNames).containsAll(R20_3_BASELINE_MATRIX.values());
        assertThat(R20_3_BASELINE_MATRIX.values()).doesNotContain(
            "plainJdbcFetchOptional",
            "plainJdbcReusableStatementFetchOptional",
            "plainJdbcFindByIdFetchOptional",
            "plainJdbcReusableFindByIdFetchOptional",
            "mortarJdbcFetchOptional",
            "mortarPreRenderedJdbcFetchOptional",
            "mortarGeneratedJdbcFetch",
            "mortarPreparedGeneratedJdbcFetch",
            "mortarJoinPageFetch",
            "mortarUpdateBatch"
        );
    }

    @Test
    void r20GeneratedFixedReadMatrixUsesOnlyMatchedFindByIdScenarioNames() {
        Set<String> methodNames = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(R20_4_GENERATED_FIXED_READ_MATRIX)
            .containsKeys(
                "ordinary-jdbc-find-by-id",
                "reusable-prepared-jdbc-find-by-id",
                "tuned-pgjdbc-reusable-jdbc-find-by-id",
                "mortar-processor-generated-find-by-id",
                "mortar-prepared-processor-generated-find-by-id",
                "mortar-tuned-processor-generated-find-by-id"
            );
        assertThat(methodNames).containsAll(R20_4_GENERATED_FIXED_READ_MATRIX.values());
        assertThat(R20_4_GENERATED_FIXED_READ_MATRIX.values()).doesNotContain(
            "plainJdbcFindByIdFetchOptional",
            "plainJdbcReusableFindByIdFetchOptional",
            "mortarProcessorGeneratedFindByIdFetchOptional",
            "mortarPreparedProcessorGeneratedFindByIdFetchOptional",
            "mortarGeneratedJdbcFetch",
            "mortarPreparedGeneratedJdbcFetch",
            "mortarJdbcFetch",
            "mortarPreRenderedJdbcFetch",
            "plainJdbcJoinPageFetch",
            "mortarJoinPageFetch",
            "plainJdbcUpdateBatch",
            "mortarUpdateBatch",
            "jooqFetch",
            "querydslFetch"
        );
    }

    @Test
    void r20GeneratedFixedReadIncludeRegexSelectsOnlyMatchedFindByIdScenarioNames() {
        Set<String> selectedMethods = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .filter(methodName -> ("PostgresExecutionBenchmark." + methodName)
                .matches(R20_4_GENERATED_FIXED_READ_INCLUDE_REGEX))
            .collect(Collectors.toSet());

        assertThat(selectedMethods).containsExactlyInAnyOrderElementsOf(R20_4_GENERATED_FIXED_READ_MATRIX.values());
    }

    @Test
    void documentsTunedPgjdbcScenarioParameters() {
        assertThat(PostgresExecutionBenchmark.TUNED_PGJDBC_PARAMETERS)
            .contains("prepareThreshold=1")
            .contains("preparedStatementCacheQueries=256")
            .contains("binaryTransfer=true");
    }

    @Test
    void processorGeneratedFindByIdBenchmarkUsesGeneratedMetamodelQuery() {
        QBenchmarkClient.FindByIdQuery query = QBenchmarkClient.BENCHMARK_CLIENT.findById(new PostgresQueryRenderer());

        assertThat(query.sql()).isEqualTo("select c.id, c.name, c.active from clients c where c.id = ?");
        assertThat(query.parameterTypes()).containsExactly(Long.class);
    }
}
