package example;

import dev.mortar.core.QueryRenderer;

final class UnsupportedAliasRepository {
    void read(QueryRenderer renderer, Long id) {
        var client = (QClient.CLIENT);
        client.read(renderer).findById(id);
    }
}
