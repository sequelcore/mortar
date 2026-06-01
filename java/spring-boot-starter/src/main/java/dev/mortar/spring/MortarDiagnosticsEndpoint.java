package dev.mortar.spring;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

@Endpoint(id = "mortar")
public class MortarDiagnosticsEndpoint {
    private final MortarSpringProperties properties;

    public MortarDiagnosticsEndpoint(MortarSpringProperties properties) {
        this.properties = properties;
    }

    @ReadOperation
    public DiagnosticsDescriptor diagnostics() {
        return new DiagnosticsDescriptor(
            "UP",
            properties.getSqlFormat().name(),
            properties.getJdbc().getLogging().isEnabled(),
            properties.getDiagnostics().isEnabled()
        );
    }

    public record DiagnosticsDescriptor(
        String status,
        String sqlFormat,
        boolean jdbcLoggingEnabled,
        boolean diagnosticsEnabled
    ) {
    }
}
