package dev.mortar.core;

public record MortarPage(int page, int size) {
    public MortarPage {
        if (page < 0) {
            throw new IllegalArgumentException("page cannot be negative");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size must be greater than zero");
        }
    }

    public static MortarPage of(int page, int size) {
        return new MortarPage(page, size);
    }

    public int offset() {
        return Math.multiplyExact(page, size);
    }
}
