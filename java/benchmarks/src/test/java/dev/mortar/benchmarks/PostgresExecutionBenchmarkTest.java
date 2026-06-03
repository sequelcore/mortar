package dev.mortar.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final Map<String, String> R20_5_DSL_SHAPES_MATRIX = Map.ofEntries(
        Map.entry("ordinary-jdbc-simple-read", "plainJdbcFetch"),
        Map.entry("mortar-dsl-render-per-call-simple-read", "mortarJdbcFetch"),
        Map.entry("mortar-pre-rendered-simple-read", "mortarPreRenderedJdbcFetch"),
        Map.entry("reusable-prepared-jdbc-join-page", "plainJdbcJoinPageFetch"),
        Map.entry("mortar-dsl-join-page", "mortarJoinPageFetch"),
        Map.entry("reusable-prepared-jdbc-update-batch", "plainJdbcUpdateBatch"),
        Map.entry("mortar-dsl-update-batch", "mortarUpdateBatch")
    );
    private static final Map<String, String> R23_2_POST_R22_JAVA_RUNTIME_MATRIX = Map.ofEntries(
        Map.entry("ordinary-jdbc-count-scalar", "plainJdbcCountActive"),
        Map.entry("mortar-count-scalar", "mortarCountActive"),
        Map.entry("ordinary-jdbc-exists-scalar", "plainJdbcExistsActive"),
        Map.entry("mortar-exists-scalar", "mortarExistsActive"),
        Map.entry("ordinary-jdbc-insert-row-count", "plainJdbcInsertRowCount"),
        Map.entry("mortar-insert-row-count", "mortarInsertRowCount"),
        Map.entry("ordinary-jdbc-update-row-count", "plainJdbcUpdateRowCount"),
        Map.entry("mortar-update-row-count", "mortarUpdateRowCount"),
        Map.entry("ordinary-jdbc-delete-row-count", "plainJdbcDeleteRowCount"),
        Map.entry("mortar-delete-row-count", "mortarDeleteRowCount"),
        Map.entry("ordinary-jdbc-insert-returning-fetch", "plainJdbcInsertReturningFetch"),
        Map.entry("mortar-insert-returning-fetch", "mortarInsertReturningFetch"),
        Map.entry("ordinary-jdbc-insert-returning-fetch-optional", "plainJdbcInsertReturningFetchOptional"),
        Map.entry("mortar-insert-returning-fetch-optional", "mortarInsertReturningFetchOptional"),
        Map.entry("reusable-prepared-jdbc-update-batch", "plainJdbcUpdateBatch"),
        Map.entry("mortar-same-sql-update-batch", "mortarUpdateBatch")
    );
    private static final String R20_4_GENERATED_FIXED_READ_INCLUDE_REGEX =
        "PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|"
            + "plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|"
            + "mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$";
    private static final String R20_5_DSL_SHAPES_INCLUDE_REGEX =
        "PostgresExecutionBenchmark\\.(plainJdbcFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|"
            + "plainJdbcJoinPageFetch|mortarJoinPageFetch|plainJdbcUpdateBatch|mortarUpdateBatch)$";
    private static final String R23_2_POST_R22_JAVA_RUNTIME_INCLUDE_REGEX =
        "PostgresExecutionBenchmark\\.(plainJdbcCountActive|mortarCountActive|plainJdbcExistsActive|"
            + "mortarExistsActive|plainJdbcInsertRowCount|mortarInsertRowCount|plainJdbcUpdateRowCount|"
            + "mortarUpdateRowCount|plainJdbcDeleteRowCount|mortarDeleteRowCount|plainJdbcInsertReturningFetch|"
            + "mortarInsertReturningFetch|plainJdbcInsertReturningFetchOptional|mortarInsertReturningFetchOptional|"
            + "plainJdbcUpdateBatch|mortarUpdateBatch)$";

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
    void r20DslShapesMatrixUsesOnlyMatchedLivePostgresDslScenarioNames() {
        Set<String> methodNames = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(R20_5_DSL_SHAPES_MATRIX)
            .containsKeys(
                "ordinary-jdbc-simple-read",
                "mortar-dsl-render-per-call-simple-read",
                "mortar-pre-rendered-simple-read",
                "reusable-prepared-jdbc-join-page",
                "mortar-dsl-join-page",
                "reusable-prepared-jdbc-update-batch",
                "mortar-dsl-update-batch"
            );
        assertThat(methodNames).containsAll(R20_5_DSL_SHAPES_MATRIX.values());
        assertThat(R20_5_DSL_SHAPES_MATRIX.values()).doesNotContain(
            "plainJdbcReusableStatementFetch",
            "plainJdbcFetchOptional",
            "mortarJdbcFetchOptional",
            "mortarPreRenderedJdbcFetchOptional",
            "plainJdbcFindByIdFetch",
            "plainJdbcReusableFindByIdFetch",
            "plainJdbcTunedReusableFindByIdFetch",
            "mortarProcessorGeneratedFindByIdFetch",
            "mortarPreparedProcessorGeneratedFindByIdFetch",
            "mortarTunedProcessorGeneratedFindByIdFetch",
            "mortarGeneratedJdbcFetch",
            "mortarPreparedGeneratedJdbcFetch",
            "jooqFetch",
            "querydslFetch"
        );
    }

    @Test
    void r20DslShapesIncludeRegexSelectsOnlyMatchedDslScenarioNames() {
        Set<String> selectedMethods = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .filter(methodName -> ("PostgresExecutionBenchmark." + methodName)
                .matches(R20_5_DSL_SHAPES_INCLUDE_REGEX))
            .collect(Collectors.toSet());

        assertThat(selectedMethods).containsExactlyInAnyOrderElementsOf(R20_5_DSL_SHAPES_MATRIX.values());
    }

    @Test
    void r23PostR22JavaRuntimeMatrixUsesOnlyScalarMutationReturningAndBatchScenarioNames() {
        Set<String> methodNames = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .collect(Collectors.toSet());

        assertThat(R23_2_POST_R22_JAVA_RUNTIME_MATRIX)
            .containsKeys(
                "ordinary-jdbc-count-scalar",
                "mortar-count-scalar",
                "ordinary-jdbc-exists-scalar",
                "mortar-exists-scalar",
                "ordinary-jdbc-insert-row-count",
                "mortar-insert-row-count",
                "ordinary-jdbc-update-row-count",
                "mortar-update-row-count",
                "ordinary-jdbc-delete-row-count",
                "mortar-delete-row-count",
                "ordinary-jdbc-insert-returning-fetch",
                "mortar-insert-returning-fetch",
                "ordinary-jdbc-insert-returning-fetch-optional",
                "mortar-insert-returning-fetch-optional",
                "reusable-prepared-jdbc-update-batch",
                "mortar-same-sql-update-batch"
            );
        assertThat(methodNames).containsAll(R23_2_POST_R22_JAVA_RUNTIME_MATRIX.values());
        assertThat(R23_2_POST_R22_JAVA_RUNTIME_MATRIX.values()).doesNotContain(
            "plainJdbcFetch",
            "plainJdbcReusableStatementFetch",
            "plainJdbcFindByIdFetch",
            "plainJdbcReusableFindByIdFetch",
            "plainJdbcTunedReusableFindByIdFetch",
            "mortarJdbcFetch",
            "mortarPreRenderedJdbcFetch",
            "mortarProcessorGeneratedFindByIdFetch",
            "mortarPreparedProcessorGeneratedFindByIdFetch",
            "mortarTunedProcessorGeneratedFindByIdFetch",
            "jooqFetch",
            "querydslFetch"
        );
    }

    @Test
    void r23PostR22JavaRuntimeIncludeRegexSelectsOnlyPostR22ScenarioNames() {
        Set<String> selectedMethods = Arrays.stream(PostgresExecutionBenchmark.class.getDeclaredMethods())
            .map(Method::getName)
            .filter(methodName -> ("PostgresExecutionBenchmark." + methodName)
                .matches(R23_2_POST_R22_JAVA_RUNTIME_INCLUDE_REGEX))
            .collect(Collectors.toSet());

        assertThat(selectedMethods)
            .containsExactlyInAnyOrderElementsOf(R23_2_POST_R22_JAVA_RUNTIME_MATRIX.values());
    }

    @Test
    void r23PostR22JavaRuntimeGradlePresetsUseR23NamesAndMatrix() throws Exception {
        String build = Files.readString(repositoryRoot().resolve("java/benchmarks/build.gradle.kts"));
        String r23Includes = build.substring(
            build.indexOf("val r23PostR22JavaRuntimeIncludes"),
            build.indexOf("dependencies {")
        );

        assertThat(build)
            .contains("jmhR23PostR22JavaRuntime")
            .contains("jmhR23PostR22JavaRuntimeAllocation")
            .contains("jmhR23PostR22JavaRuntimeLatency")
            .contains("r23.2-post-r22-java-runtime-throughput.json")
            .contains("r23.2-post-r22-java-runtime-allocation.json")
            .contains("r23.2-post-r22-java-runtime-latency.json");
        assertThat(r23Includes)
            .contains("plainJdbcCountActive")
            .contains("mortarCountActive")
            .contains("plainJdbcExistsActive")
            .contains("mortarExistsActive")
            .contains("plainJdbcInsertRowCount")
            .contains("mortarInsertRowCount")
            .contains("plainJdbcUpdateRowCount")
            .contains("mortarUpdateRowCount")
            .contains("plainJdbcDeleteRowCount")
            .contains("mortarDeleteRowCount")
            .contains("plainJdbcInsertReturningFetch")
            .contains("mortarInsertReturningFetch")
            .contains("plainJdbcInsertReturningFetchOptional")
            .contains("mortarInsertReturningFetchOptional")
            .contains("plainJdbcUpdateBatch")
            .contains("mortarUpdateBatch")
            .doesNotContain("JdbcExecutionBenchmark")
            .doesNotContain("PostgresRenderingBenchmark")
            .doesNotContain("ReferenceRenderingBenchmark")
            .doesNotContain("jooqFetch")
            .doesNotContain("querydslFetch");
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

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                && Files.exists(current.resolve("CLAUDE.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
