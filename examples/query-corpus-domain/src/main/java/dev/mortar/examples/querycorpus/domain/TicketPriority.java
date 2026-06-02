package dev.mortar.examples.querycorpus.domain;

import java.util.Objects;

public enum TicketPriority {
    CRITICAL("critical"),
    HIGH("high"),
    NORMAL("normal"),
    LOW("low");

    private final String code;

    TicketPriority(String code) {
        this.code = Objects.requireNonNull(code, "code cannot be null");
    }

    public String code() {
        return code;
    }
}
