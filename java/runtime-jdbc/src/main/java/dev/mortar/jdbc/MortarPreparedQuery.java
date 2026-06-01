package dev.mortar.jdbc;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable prepared generated query tied to a caller-owned JDBC connection.
 */
public final class MortarPreparedQuery<P, T> implements AutoCloseable {
    private final PreparedStatement statement;
    private final MortarGeneratedQuery<P, T> query;
    private final MortarJdbcLogger logger;
    private final boolean loggingEnabled;

    MortarPreparedQuery(
        PreparedStatement statement,
        MortarGeneratedQuery<P, T> query,
        MortarJdbcLogger logger,
        boolean loggingEnabled
    ) {
        this.statement = Objects.requireNonNull(statement, "statement cannot be null");
        this.query = Objects.requireNonNull(query, "query cannot be null");
        this.logger = Objects.requireNonNull(logger, "logger cannot be null");
        this.loggingEnabled = loggingEnabled;
    }

    public List<T> fetch(P parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        log();

        try {
            query.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(query.map(resultSet));
                }
                return List.copyOf(rows);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException(
                "Failed to execute Mortar prepared generated query",
                MortarGeneratedQueryContext.rendered(query),
                exception
            );
        }
    }

    public Optional<T> fetchOptional(P parameters) {
        Objects.requireNonNull(parameters, "parameters cannot be null");
        log();

        try {
            query.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                T row = query.map(resultSet);
                if (resultSet.next()) {
                    throw new IllegalStateException("expected at most one row");
                }
                return Optional.ofNullable(row);
            }
        } catch (SQLException exception) {
            throw new MortarJdbcException(
                "Failed to execute Mortar prepared generated query",
                MortarGeneratedQueryContext.rendered(query),
                exception
            );
        }
    }

    @Override
    public void close() throws SQLException {
        statement.close();
    }

    private void log() {
        if (!loggingEnabled) {
            return;
        }
        logger.log(new MortarJdbcLogEvent(
            MortarJdbcOperation.QUERY,
            query.sql(),
            MortarGeneratedQueryContext.redactedParameters(query),
            query.metadata()
        ));
    }
}
