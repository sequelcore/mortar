package example;

import dev.mortar.core.QueryRenderer;

final class ClientRepository {
    void read(QueryRenderer renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }
}
