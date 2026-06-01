package dev.mortar.core;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable diagnostic emitted by core analysis or tooling.
 */
public record MortarDiagnostic(
    MortarDiagnosticCode code,
    MortarDiagnosticSeverity severity,
    String message,
    Optional<MortarLocation> location
) {
    public MortarDiagnostic {
        Objects.requireNonNull(code, "code cannot be null");
        Objects.requireNonNull(severity, "severity cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    public static MortarDiagnostic error(MortarDiagnosticCode code, String message) {
        return new MortarDiagnostic(code, MortarDiagnosticSeverity.ERROR, message, Optional.empty());
    }

    public static MortarDiagnostic error(MortarDiagnosticCode code, String message, MortarLocation location) {
        return new MortarDiagnostic(code, MortarDiagnosticSeverity.ERROR, message, Optional.of(location));
    }

    public static MortarDiagnostic warning(MortarDiagnosticCode code, String message) {
        return new MortarDiagnostic(code, MortarDiagnosticSeverity.WARNING, message, Optional.empty());
    }

    public static MortarDiagnostic info(MortarDiagnosticCode code, String message) {
        return new MortarDiagnostic(code, MortarDiagnosticSeverity.INFO, message, Optional.empty());
    }
}
