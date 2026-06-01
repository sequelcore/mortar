package dev.mortar.jdbc;

import java.util.Objects;

/**
 * Parameter value or redacted parameter metadata for JDBC logging.
 */
public record MortarJdbcParameter(Object value, Class<?> javaType, boolean redacted) {
    public MortarJdbcParameter {
        Objects.requireNonNull(javaType, "javaType cannot be null");
    }

    public static MortarJdbcParameter redacted(Class<?> javaType) {
        return new MortarJdbcParameter(null, javaType, true);
    }
}
