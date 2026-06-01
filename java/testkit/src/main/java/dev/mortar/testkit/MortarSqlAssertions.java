package dev.mortar.testkit;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Join;
import dev.mortar.core.Parameter;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.TableRef;

import org.assertj.core.api.AbstractAssert;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class MortarSqlAssertions extends AbstractAssert<MortarSqlAssertions, RenderedQuery> {
    private MortarSqlAssertions(RenderedQuery renderedQuery) {
        super(renderedQuery, MortarSqlAssertions.class);
    }

    public static MortarSqlAssertions assertThatSql(RenderedQuery renderedQuery) {
        return new MortarSqlAssertions(renderedQuery);
    }

    public MortarSqlAssertions hasSql(String expectedSql) {
        Objects.requireNonNull(expectedSql, "expectedSql cannot be null");
        isNotNull();
        if (!actual.sql().equals(expectedSql)) {
            failWithMessage("""
                Expected SQL to be:
                  <%s>
                but was:
                  <%s>""", expectedSql, actual.sql());
        }
        return this;
    }

    public MortarSqlAssertions renders(String expectedSql) {
        return hasSql(expectedSql);
    }

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
                  <%s>""", expected, actualValues, actual.sql());
        }
        return this;
    }

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
                  <%s>""", expectedTypeNames, actualTypeNames, actual.sql());
        }
        return this;
    }

    public MortarSqlAssertions hasTables(TableRef... expectedTables) {
        Objects.requireNonNull(expectedTables, "expectedTables cannot be null");
        isNotNull();
        return hasMetadata("tables", Arrays.asList(expectedTables), actual.metadata().tables());
    }

    public MortarSqlAssertions hasColumns(ColumnRef<?>... expectedColumns) {
        Objects.requireNonNull(expectedColumns, "expectedColumns cannot be null");
        isNotNull();
        return hasMetadata("columns", Arrays.asList(expectedColumns), actual.metadata().columns());
    }

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
                  <%s>""", metadataName, expected, actualValues, actual.sql());
        }
        return this;
    }
}
