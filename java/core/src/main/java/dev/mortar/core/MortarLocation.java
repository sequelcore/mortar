package dev.mortar.core;

import java.util.Objects;

public record MortarLocation(String source, int line, int column) {
    public MortarLocation {
        Objects.requireNonNull(source, "source cannot be null");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source cannot be blank");
        }
        if (line < 1) {
            throw new IllegalArgumentException("line must be greater than zero");
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be greater than zero");
        }
    }

    public static MortarLocation generated(String source, int line, int column) {
        return new MortarLocation(source, line, column);
    }
}
