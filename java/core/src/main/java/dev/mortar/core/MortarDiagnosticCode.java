package dev.mortar.core;

public enum MortarDiagnosticCode {
    INVALID_QUERY("MORTAR_CORE_001"),
    UNBOUNDED_QUERY("MORTAR_CORE_002"),
    UNSAFE_RAW_SQL("MORTAR_CORE_003"),
    UNSUPPORTED_DIALECT_FEATURE("MORTAR_CORE_004"),
    SELECT_ALL("MORTAR_CORE_005"),
    UNSTABLE_PAGINATION("MORTAR_CORE_006"),
    LARGE_IN_LIST("MORTAR_CORE_007"),
    NULLABLE_RELATION_INNER_JOIN("MORTAR_CORE_008"),
    REPEATED_QUERY_PATTERN("MORTAR_CORE_009"),
    INDEX_ADVISORY("MORTAR_CORE_010");

    private final String stableCode;

    MortarDiagnosticCode(String stableCode) {
        this.stableCode = stableCode;
    }

    public String stableCode() {
        return stableCode;
    }
}
