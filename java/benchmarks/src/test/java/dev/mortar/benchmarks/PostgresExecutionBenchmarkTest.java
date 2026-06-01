package dev.mortar.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import dev.mortar.postgres.PostgresQueryRenderer;

import org.junit.jupiter.api.Test;

final class PostgresExecutionBenchmarkTest {
    @Test
    void fixtureDefinesDeterministicIndexedLookupDataset() {
        assertThat(PostgresExecutionBenchmark.DATASET_SIZE).isEqualTo(1_000);
        assertThat(PostgresExecutionBenchmark.QUERY_CLIENT_ID).isEqualTo(777L);
        assertThat(PostgresExecutionBenchmark.CREATE_SCHEMA_SQL)
            .contains("id bigint primary key")
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
            "jooqFetch",
            "jooqFetchOptional",
            "querydslFetch",
            "querydslFetchOptional"
        );
    }

    @Test
    void processorGeneratedFindByIdBenchmarkUsesGeneratedMetamodelQuery() {
        QBenchmarkClient.FindByIdQuery query = QBenchmarkClient.BENCHMARK_CLIENT.findById(new PostgresQueryRenderer());

        assertThat(query.sql()).isEqualTo("select c.id, c.name, c.active from clients c where c.id = ?");
        assertThat(query.parameterTypes()).containsExactly(Long.class);
    }
}
