package dev.mortar.core;

import java.util.Objects;

public record Parameter(Object value, Class<?> javaType) {
    public Parameter {
        Objects.requireNonNull(javaType, "javaType cannot be null");
    }

    public static Parameter of(Object value) {
        return new Parameter(value, value == null ? Object.class : value.getClass());
    }
}
