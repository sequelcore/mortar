package dev.mortar.examples.cleanpostgres;

import java.util.Optional;

public interface ClientWriter {
    Optional<ClientSummary> create(long id, String name, boolean active);

    int rename(long id, String name);

    int delete(long id);
}
