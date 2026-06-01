package dev.mortar.spring;

import dev.mortar.core.QueryRenderer;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Actuator endpoint that reports effective Mortar starter wiring.
 */
@Endpoint(id = "mortar")
public class MortarDiagnosticsEndpoint {
    private final MortarSpringProperties properties;
    private final QueryRenderer renderer;

    /**
     * Creates diagnostics for the active starter properties and renderer.
     *
     * @param properties bound starter properties
     * @param renderer active query renderer
     */
    public MortarDiagnosticsEndpoint(MortarSpringProperties properties, QueryRenderer renderer) {
        this.properties = properties;
        this.renderer = renderer;
    }

    /**
     * Returns non-secret diagnostics for the active Mortar starter wiring.
     *
     * @return diagnostics descriptor
     */
    @ReadOperation
    public DiagnosticsDescriptor diagnostics() {
        return new DiagnosticsDescriptor(
            "UP",
            properties.getDialect().name(),
            properties.getSqlFormat().name(),
            properties.getJdbc().getLogging().isEnabled(),
            properties.getDiagnostics().isEnabled(),
            renderer.getClass().getName()
        );
    }

    /**
     * Non-secret diagnostics payload for the active Mortar starter configuration.
     *
     * @param status endpoint status
     * @param dialect configured dialect
     * @param sqlFormat configured SQL formatting mode
     * @param jdbcLoggingEnabled whether JDBC logging is enabled
     * @param diagnosticsEnabled whether diagnostics are enabled
     * @param renderer active renderer class name
     */
    public record DiagnosticsDescriptor(
        String status,
        String dialect,
        String sqlFormat,
        boolean jdbcLoggingEnabled,
        boolean diagnosticsEnabled,
        String renderer
    ) {
    }
}
