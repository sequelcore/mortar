package dev.mortar.core;

/**
 * Dialect boundary that renders query and mutation specs to SQL.
 */
public interface QueryRenderer {
    RenderedQuery render(QuerySpec query);

    default RenderedQuery render(InsertSpec insert) {
        throw new UnsupportedOperationException("Insert rendering is not supported");
    }

    default RenderedQuery render(UpdateSpec update) {
        throw new UnsupportedOperationException("Update rendering is not supported");
    }

    default RenderedQuery render(DeleteSpec delete) {
        throw new UnsupportedOperationException("Delete rendering is not supported");
    }
}
