package dev.mortar.testkit;

import static dev.mortar.testkit.MortarExplainPlanAssertions.assertThatExplainPlan;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class MortarExplainPlanAssertionsTest {
    @Test
    void assertsExplainPlanNode() {
        assertThatExplainPlan("""
            Index Scan using clients_pkey on clients c
              Index Cond: (id = 7)""")
            .containsNode("Index Scan")
            .usesIndex("clients_pkey")
            .doesNotUseSequentialScan("clients");
    }

    @Test
    void reportsMissingExplainPlanNode() {
        assertThatThrownBy(() -> assertThatExplainPlan("Seq Scan on clients c").containsNode("Index Scan"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected explain plan to contain node:
                  <Index Scan>
                but plan was:
                  <Seq Scan on clients c>""");
    }

    @Test
    void reportsSequentialScanUsage() {
        assertThatThrownBy(() -> assertThatExplainPlan("Seq Scan on clients c").doesNotUseSequentialScan("clients"))
            .isInstanceOf(AssertionError.class)
            .hasMessage("""
                Expected explain plan not to use sequential scan on:
                  <clients>
                but plan was:
                  <Seq Scan on clients c>""");
    }

    @Test
    void rejectsNullExplainPlan() {
        assertThatThrownBy(() -> assertThatExplainPlan(null).containsNode("Index Scan"))
            .isInstanceOf(AssertionError.class);
    }
}
