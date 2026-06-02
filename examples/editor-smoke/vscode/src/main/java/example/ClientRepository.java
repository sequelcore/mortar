package example;

import dev.mortar.core.QueryRenderer;

final class ClientRepository {
    void readCanonical(QueryRenderer renderer, Long id) {
        QClient.CLIENT.read(renderer).findById(id);
    }

    void readMetamodelAlias(QueryRenderer renderer, Long id) {
        var client = QClient.CLIENT;
        client.read(renderer).findById(id);
    }

    void readNamespaceAlias(QueryRenderer renderer, Long id) {
        var read = QClient.CLIENT.read(renderer);
        read.findById(id);
    }
}
