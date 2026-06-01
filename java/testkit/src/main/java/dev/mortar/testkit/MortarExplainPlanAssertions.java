package dev.mortar.testkit;

import org.assertj.core.api.AbstractAssert;

import java.util.Objects;

/**
 * AssertJ assertions for PostgreSQL EXPLAIN text.
 */
public final class MortarExplainPlanAssertions extends AbstractAssert<MortarExplainPlanAssertions, String> {
    private MortarExplainPlanAssertions(String explainPlan) {
        super(explainPlan, MortarExplainPlanAssertions.class);
    }

    public static MortarExplainPlanAssertions assertThatExplainPlan(String explainPlan) {
        return new MortarExplainPlanAssertions(explainPlan);
    }

    public MortarExplainPlanAssertions containsNode(String expectedNode) {
        Objects.requireNonNull(expectedNode, "expectedNode cannot be null");
        isNotNull();
        if (!actual.contains(expectedNode)) {
            failWithMessage("""
                Expected explain plan to contain node:
                  <%s>
                but plan was:
                  <%s>""", expectedNode, actual);
        }
        return this;
    }

    public MortarExplainPlanAssertions usesIndex(String indexName) {
        Objects.requireNonNull(indexName, "indexName cannot be null");
        isNotNull();
        if (!actual.contains(indexName)) {
            failWithMessage("""
                Expected explain plan to use index:
                  <%s>
                but plan was:
                  <%s>""", indexName, actual);
        }
        return this;
    }

    public MortarExplainPlanAssertions doesNotUseSequentialScan(String tableName) {
        Objects.requireNonNull(tableName, "tableName cannot be null");
        isNotNull();
        String sequentialScan = "Seq Scan on " + tableName;
        if (actual.contains(sequentialScan)) {
            failWithMessage("""
                Expected explain plan not to use sequential scan on:
                  <%s>
                but plan was:
                  <%s>""", tableName, actual);
        }
        return this;
    }
}
