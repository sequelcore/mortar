package dev.mortar.testkit;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Join;
import dev.mortar.core.MortarBoundMutation;
import dev.mortar.core.MortarBoundQuery;
import dev.mortar.core.MortarBoundScalar;
import dev.mortar.core.MortarReturningMutation;
import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.TableRef;

import org.assertj.core.api.AbstractAssert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * AssertJ assertions for rendered SQL, parameters, and metadata.
 */
public final class MortarSqlAssertions extends AbstractAssert<MortarSqlAssertions, RenderedQuery> {
    private final String queryName;
    private final Class<?> rowType;

    private MortarSqlAssertions(RenderedQuery renderedQuery) {
        this(renderedQuery, null, null);
    }

    private MortarSqlAssertions(RenderedQuery renderedQuery, String queryName, Class<?> rowType) {
        super(renderedQuery, MortarSqlAssertions.class);
        this.queryName = queryName;
        this.rowType = rowType;
    }

    /**
     * Starts assertions for already rendered SQL.
     */
    public static MortarSqlAssertions assertThatSql(RenderedQuery renderedQuery) {
        return new MortarSqlAssertions(renderedQuery);
    }

    /**
     * Starts assertions for a bound read query, including its optional name and
     * row type context.
     */
    public static MortarSqlAssertions assertThatSql(MortarBoundQuery<?> query) {
        Objects.requireNonNull(query, "query cannot be null");
        return new MortarSqlAssertions(query.rendered(), query.queryName().orElse("<unnamed>"), query.rowType());
    }

    /**
     * Starts assertions for a bound scalar query.
     */
    public static MortarSqlAssertions assertThatSql(MortarBoundScalar<?> scalar) {
        Objects.requireNonNull(scalar, "scalar cannot be null");
        return new MortarSqlAssertions(scalar.rendered(), scalar.queryName().orElse("<unnamed>"), scalar.scalarType());
    }

    /**
     * Starts assertions for a row-count mutation.
     */
    public static MortarSqlAssertions assertThatSql(MortarBoundMutation mutation) {
        Objects.requireNonNull(mutation, "mutation cannot be null");
        return new MortarSqlAssertions(mutation.rendered(), mutation.mutationName().orElse("<unnamed>"), null);
    }

    /**
     * Starts assertions for a returning mutation.
     */
    public static MortarSqlAssertions assertThatSql(MortarReturningMutation<?> mutation) {
        Objects.requireNonNull(mutation, "mutation cannot be null");
        return new MortarSqlAssertions(mutation.rendered(), mutation.mutationName().orElse("<unnamed>"), mutation.rowType());
    }

    /**
     * Verifies the exact rendered SQL string.
     */
    public MortarSqlAssertions hasSql(String expectedSql) {
        Objects.requireNonNull(expectedSql, "expectedSql cannot be null");
        isNotNull();
        if (!actual.sql().equals(expectedSql)) {
            failWithMessage("""
                Expected SQL to be:
                  <%s>
                but was:
                  <%s>%s""", expectedSql, actual.sql(), queryContext());
        }
        return this;
    }

    /**
     * Alias for {@link #hasSql(String)}.
     */
    public MortarSqlAssertions renders(String expectedSql) {
        return hasSql(expectedSql);
    }

    /**
     * Verifies the query or mutation inspection name.
     */
    public MortarSqlAssertions hasName(String expectedName) {
        Objects.requireNonNull(expectedName, "expectedName cannot be null");
        if (queryName == null || !queryName.equals(expectedName)) {
            failWithMessage("""
                Expected SQL contract name to be:
                  <%s>
                but was:
                  <%s>""", expectedName, queryName);
        }
        return this;
    }

    /**
     * Verifies ordered rendered parameter values.
     */
    public MortarSqlAssertions hasParameters(Object... expectedValues) {
        Objects.requireNonNull(expectedValues, "expectedValues cannot be null");
        isNotNull();

        List<Object> expected = Arrays.asList(expectedValues);
        List<Object> actualValues = actual.parameters().stream()
            .map(Parameter::value)
            .toList();

        if (!actualValues.equals(expected)) {
            failWithMessage("""
                Expected SQL parameters to be:
                  <%s>
                but were:
                  <%s>
                SQL:
                  <%s>%s""", expected, actualValues, actual.sql(), queryContext());
        }
        return this;
    }

    /**
     * Verifies ordered rendered Java parameter types.
     */
    public MortarSqlAssertions hasParameterTypes(Class<?>... expectedTypes) {
        Objects.requireNonNull(expectedTypes, "expectedTypes cannot be null");
        isNotNull();

        List<String> expectedTypeNames = Arrays.stream(expectedTypes)
            .map(expectedType -> Objects.requireNonNull(expectedType, "expectedTypes cannot contain null"))
            .map(Class::getName)
            .toList();
        List<String> actualTypeNames = actual.parameters().stream()
            .map(Parameter::javaType)
            .map(Class::getName)
            .toList();

        if (!actualTypeNames.equals(expectedTypeNames)) {
            failWithMessage("""
                Expected SQL parameter types to be:
                  <%s>
                but were:
                  <%s>
                SQL:
                  <%s>%s""", expectedTypeNames, actualTypeNames, actual.sql(), queryContext());
        }
        return this;
    }

    /**
     * Verifies rendered table metadata.
     */
    public MortarSqlAssertions hasTables(TableRef... expectedTables) {
        Objects.requireNonNull(expectedTables, "expectedTables cannot be null");
        isNotNull();
        return hasMetadata("tables", Arrays.asList(expectedTables), actual.metadata().tables());
    }

    /**
     * Verifies rendered column metadata.
     */
    public MortarSqlAssertions hasColumns(ColumnRef<?>... expectedColumns) {
        Objects.requireNonNull(expectedColumns, "expectedColumns cannot be null");
        isNotNull();
        return hasMetadata("columns", Arrays.asList(expectedColumns), actual.metadata().columns());
    }

    /**
     * Verifies rendered join metadata.
     */
    public MortarSqlAssertions hasJoins(Join... expectedJoins) {
        Objects.requireNonNull(expectedJoins, "expectedJoins cannot be null");
        isNotNull();
        return hasMetadata("joins", Arrays.asList(expectedJoins), actual.metadata().joins());
    }

    private <T> MortarSqlAssertions hasMetadata(String metadataName, List<T> expected, List<T> actualValues) {
        if (!actualValues.equals(expected)) {
            failWithMessage("""
                Expected query metadata %s to be:
                  <%s>
                but were:
                  <%s>
                SQL:
                  <%s>%s""", metadataName, expected, actualValues, actual.sql(), queryContext());
        }
        return this;
    }

    private String queryContext() {
        if (queryName == null) {
            return "";
        }
        if (rowType == null) {
            return """

                Query:
                  <%s>""".formatted(queryName);
        }
        return """

            Query:
              <%s>
            Row type:
              <%s>""".formatted(queryName, rowType.getName());
    }
}
