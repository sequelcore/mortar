package dev.mortar.examples.cleanpostgres;

import java.util.List;
import java.util.Optional;

public interface ClientReader {
    Optional<ClientSummary> findById(long id);

    List<ClientSummary> findActivePage(int page, int size);
}
