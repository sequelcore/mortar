package dev.mortar.examples.querycorpus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class TicketPriorityTest {
    @Test
    void definesStablePriorityCodesForFixtureQueries() {
        assertThat(TicketPriority.CRITICAL.code()).isEqualTo("critical");
        assertThat(TicketPriority.HIGH.code()).isEqualTo("high");
        assertThat(TicketPriority.NORMAL.code()).isEqualTo("normal");
        assertThat(TicketPriority.LOW.code()).isEqualTo("low");
    }
}
