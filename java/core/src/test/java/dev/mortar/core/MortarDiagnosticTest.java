package dev.mortar.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class MortarDiagnosticTest {
    @Test
    void createsStableErrorDiagnostic() {
        MortarDiagnostic diagnostic = MortarDiagnostic.error(
            MortarDiagnosticCode.INVALID_QUERY,
            "Query cannot be rendered",
            MortarLocation.generated("ClientQuery", 12, 8)
        );

        assertThat(diagnostic.code().stableCode()).isEqualTo("MORTAR_CORE_001");
        assertThat(diagnostic.severity()).isEqualTo(MortarDiagnosticSeverity.ERROR);
        assertThat(diagnostic.location())
            .hasValueSatisfying(location -> assertThat(location.source()).isEqualTo("ClientQuery"));
    }

    @Test
    void createsStableWarningDiagnosticWithoutLocation() {
        MortarDiagnostic diagnostic = MortarDiagnostic.warning(
            MortarDiagnosticCode.UNBOUNDED_QUERY,
            "Collection query has no limit"
        );

        assertThat(diagnostic.code().stableCode()).isEqualTo("MORTAR_CORE_002");
        assertThat(diagnostic.location()).isEmpty();
    }

    @Test
    void rejectsBlankDiagnosticMessage() {
        assertThatThrownBy(() -> MortarDiagnostic.error(MortarDiagnosticCode.INVALID_QUERY, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("message cannot be blank");
    }
}
