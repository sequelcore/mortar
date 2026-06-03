package dev.mortar.core;

/**
 * Dialect boundary that renders query and mutation specs to SQL.
 *
 * <p>Renderers translate semantic Mortar models into SQL and parameters. They
 * must not execute SQL or own runtime policy.</p>
 */
public interface QueryRenderer {
    /**
     * Renders a select query specification.
     */
    RenderedQuery render(QuerySpec query);

    /**
     * Renders a scalar count specification when the dialect supports it.
     */
    default RenderedQuery render(CountSpec count) {
        throw new UnsupportedOperationException("Count rendering is not supported");
    }

    /**
     * Renders a scalar exists specification when the dialect supports it.
     */
    default RenderedQuery render(ExistsSpec exists) {
        throw new UnsupportedOperationException("Exists rendering is not supported");
    }

    /**
     * Renders an insert mutation when the dialect supports it.
     */
    default RenderedQuery render(InsertSpec insert) {
        throw new UnsupportedOperationException("Insert rendering is not supported");
    }

    /**
     * Renders an update mutation when the dialect supports it.
     */
    default RenderedQuery render(UpdateSpec update) {
        throw new UnsupportedOperationException("Update rendering is not supported");
    }

    /**
     * Renders a delete mutation when the dialect supports it.
     */
    default RenderedQuery render(DeleteSpec delete) {
        throw new UnsupportedOperationException("Delete rendering is not supported");
    }
}
