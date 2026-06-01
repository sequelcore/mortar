package dev.mortar.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

final class MortarDiagnosticsEndpointTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(EndpointAutoConfiguration.class, MortarAutoConfiguration.class));

    @Test
    void exposesMortarDiagnosticsEndpointWhenDiagnosticsAreEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MortarDiagnosticsEndpoint.class);

            MortarDiagnosticsEndpoint.DiagnosticsDescriptor diagnostics = context.getBean(MortarDiagnosticsEndpoint.class)
                .diagnostics();

            assertThat(diagnostics.status()).isEqualTo("UP");
            assertThat(diagnostics.dialect()).isEqualTo("POSTGRES");
            assertThat(diagnostics.sqlFormat()).isEqualTo("COMPACT");
            assertThat(diagnostics.jdbcLoggingEnabled()).isFalse();
            assertThat(diagnostics.renderer()).isEqualTo("dev.mortar.postgres.PostgresQueryRenderer");
        });
    }

    @Test
    void exposesConfiguredStarterDiagnostics() {
        contextRunner
            .withPropertyValues(
                "mortar.dialect=postgres",
                "mortar.sql-format=pretty",
                "mortar.jdbc.logging.enabled=true"
            )
            .run(context -> {
                MortarDiagnosticsEndpoint.DiagnosticsDescriptor diagnostics = context
                    .getBean(MortarDiagnosticsEndpoint.class)
                    .diagnostics();

                assertThat(diagnostics.dialect()).isEqualTo("POSTGRES");
                assertThat(diagnostics.sqlFormat()).isEqualTo("PRETTY");
                assertThat(diagnostics.jdbcLoggingEnabled()).isTrue();
                assertThat(diagnostics.diagnosticsEnabled()).isTrue();
            });
    }

    @Test
    void disablesMortarDiagnosticsEndpointFromProperty() {
        contextRunner
            .withPropertyValues("mortar.diagnostics.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(MortarDiagnosticsEndpoint.class));
    }
}
