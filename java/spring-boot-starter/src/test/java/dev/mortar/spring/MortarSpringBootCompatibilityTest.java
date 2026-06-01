package dev.mortar.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;

final class MortarSpringBootCompatibilityTest {
    @Test
    void runsAgainstDocumentedSpringBoot35Baseline() {
        assertThat(SpringBootVersion.getVersion()).startsWith("3.5.");
    }
}
