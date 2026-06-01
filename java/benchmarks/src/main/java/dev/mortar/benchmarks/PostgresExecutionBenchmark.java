package dev.mortar.benchmarks;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.PostgreSQLTemplates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;

import dev.mortar.core.Assignment;
import dev.mortar.core.ColumnRef;
import dev.mortar.core.QueryMetadata;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.core.UpdateSpec;
import dev.mortar.jdbc.MortarGeneratedQuery;
import dev.mortar.jdbc.MortarJdbcClient;
import dev.mortar.jdbc.MortarPreparedQuery;
import dev.mortar.postgres.PostgresQueryRenderer;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class PostgresExecutionBenchmark {
    static final int DATASET_SIZE = 1_000;
    static final long QUERY_CLIENT_ID = 777L;
    static final int PAGE_SIZE = 20;
    static final int PAGE_OFFSET = 40;
    static final boolean QUERY_ACTIVE = true;
    static final String TUNED_PGJDBC_PARAMETERS =
        "prepareThreshold=1&preparedStatementCacheQueries=256&binaryTransfer=true";
    static final String CREATE_SCHEMA_SQL = """
        create table routes (
            id bigint primary key,
            name text not null
        );
        create table clients (
            id bigint primary key,
            route_id bigint not null references routes(id),
            name text not null,
            active boolean not null
        );
        create index clients_active_id_idx on clients (active, id)
        """;

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final String SELECT_SQL = "select id, name from clients where active = ? and id = ?";
    private static final String SELECT_BY_ID_SQL = "select c.id, c.name, c.active from clients c where c.id = ?";
    private static final String SELECT_JOIN_PAGE_SQL = """
        select c.id, c.name, r.name
        from clients c
        inner join routes r on c.route_id = r.id
        where c.active = ?
        order by c.id asc
        limit ? offset ?
        """.replace("\n", " ").replaceAll(" +", " ").trim();
    private static final String UPDATE_SQL = "update clients set active = ? where id = ?";

    private PostgreSQLContainer postgres;
    private Connection connection;
    private Connection tunedConnection;
    private PreparedStatement plainReusableStatement;
    private PreparedStatement plainReusableFindByIdStatement;
    private PreparedStatement plainJoinPageStatement;
    private PreparedStatement plainUpdateStatement;
    private PreparedStatement plainTunedReusableFindByIdStatement;
    private MortarJdbcClient mortarClient;
    private MortarJdbcClient tunedMortarClient;
    private QuerySpec mortarQuery;
    private QuerySpec mortarJoinPageQuery;
    private RenderedQuery mortarRenderedQuery;
    private List<UpdateSpec> mortarUpdateBatch;
    private MortarGeneratedQuery<ClientLookupParameters, ClientRow> mortarGeneratedQuery;
    private ClientLookupParameters mortarGeneratedParameters;
    private MortarPreparedQuery<ClientLookupParameters, ClientRow> mortarPreparedGeneratedQuery;
    private MortarGeneratedQuery<QBenchmarkClient.FindByIdParameters, QBenchmarkClient.FindByIdRow>
        mortarProcessorGeneratedFindByIdQuery;
    private QBenchmarkClient.FindByIdParameters mortarProcessorGeneratedFindByIdParameters;
    private MortarPreparedQuery<QBenchmarkClient.FindByIdParameters, QBenchmarkClient.FindByIdRow>
        mortarPreparedProcessorGeneratedFindByIdQuery;
    private DSLContext jooq;
    private Table<?> jooqClients;
    private Field<Long> jooqId;
    private Field<String> jooqName;
    private Field<Boolean> jooqActive;
    private Configuration querydslConfiguration;
    private RelationalPathBase<Object> querydslClients;
    private NumberPath<Long> querydslId;
    private StringPath querydslName;
    private com.querydsl.core.types.dsl.BooleanPath querydslActive;

    @Setup(Level.Trial)
    public void setUp() throws Exception {
        postgres = new PostgreSQLContainer(POSTGRES_IMAGE);
        postgres.start();
        connection = DriverManager.getConnection(
            postgres.getJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
        tunedConnection = DriverManager.getConnection(
            tunedJdbcUrl(),
            postgres.getUsername(),
            postgres.getPassword()
        );
        createSchema();
        seedClients();
        configureMortar();
        configureJooq();
        configureQuerydsl();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        if (mortarPreparedGeneratedQuery != null) {
            mortarPreparedGeneratedQuery.close();
        }
        if (mortarPreparedProcessorGeneratedFindByIdQuery != null) {
            mortarPreparedProcessorGeneratedFindByIdQuery.close();
        }
        if (plainReusableStatement != null) {
            plainReusableStatement.close();
        }
        if (plainReusableFindByIdStatement != null) {
            plainReusableFindByIdStatement.close();
        }
        if (plainJoinPageStatement != null) {
            plainJoinPageStatement.close();
        }
        if (plainUpdateStatement != null) {
            plainUpdateStatement.close();
        }
        if (plainTunedReusableFindByIdStatement != null) {
            plainTunedReusableFindByIdStatement.close();
        }
        if (connection != null) {
            connection.close();
        }
        if (tunedConnection != null) {
            tunedConnection.close();
        }
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Benchmark
    public void plainJdbcFetch(Blackhole blackhole) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setBoolean(1, QUERY_ACTIVE);
            statement.setLong(2, QUERY_CLIENT_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ClientRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new ClientRow(resultSet.getLong("id"), resultSet.getString("name")));
                }
                blackhole.consume(List.copyOf(rows));
            }
        }
    }

    @Benchmark
    public void plainJdbcFetchOptional(Blackhole blackhole) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
            statement.setBoolean(1, QUERY_ACTIVE);
            statement.setLong(2, QUERY_CLIENT_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                Optional<ClientRow> row = Optional.empty();
                if (resultSet.next()) {
                    row = Optional.of(new ClientRow(resultSet.getLong("id"), resultSet.getString("name")));
                    if (resultSet.next()) {
                        throw new IllegalStateException("expected at most one row");
                    }
                }
                blackhole.consume(row);
            }
        }
    }

    @Benchmark
    public void plainJdbcReusableStatementFetch(Blackhole blackhole) throws Exception {
        plainReusableStatement.setBoolean(1, QUERY_ACTIVE);
        plainReusableStatement.setLong(2, QUERY_CLIENT_ID);
        try (ResultSet resultSet = plainReusableStatement.executeQuery()) {
            List<ClientRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new ClientRow(resultSet.getLong("id"), resultSet.getString("name")));
            }
            blackhole.consume(List.copyOf(rows));
        }
    }

    @Benchmark
    public void plainJdbcReusableStatementFetchOptional(Blackhole blackhole) throws Exception {
        plainReusableStatement.setBoolean(1, QUERY_ACTIVE);
        plainReusableStatement.setLong(2, QUERY_CLIENT_ID);
        try (ResultSet resultSet = plainReusableStatement.executeQuery()) {
            Optional<ClientRow> row = Optional.empty();
            if (resultSet.next()) {
                row = Optional.of(new ClientRow(resultSet.getLong("id"), resultSet.getString("name")));
                if (resultSet.next()) {
                    throw new IllegalStateException("expected at most one row");
                }
            }
            blackhole.consume(row);
        }
    }

    @Benchmark
    public void plainJdbcFindByIdFetch(Blackhole blackhole) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, QUERY_CLIENT_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<QBenchmarkClient.FindByIdRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new QBenchmarkClient.FindByIdRow(
                        resultSet.getLong(1),
                        resultSet.getString(2),
                        resultSet.getBoolean(3)
                    ));
                }
                blackhole.consume(List.copyOf(rows));
            }
        }
    }

    @Benchmark
    public void plainJdbcFindByIdFetchOptional(Blackhole blackhole) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, QUERY_CLIENT_ID);
            try (ResultSet resultSet = statement.executeQuery()) {
                Optional<QBenchmarkClient.FindByIdRow> row = Optional.empty();
                if (resultSet.next()) {
                    row = Optional.of(new QBenchmarkClient.FindByIdRow(
                        resultSet.getLong(1),
                        resultSet.getString(2),
                        resultSet.getBoolean(3)
                    ));
                    if (resultSet.next()) {
                        throw new IllegalStateException("expected at most one row");
                    }
                }
                blackhole.consume(row);
            }
        }
    }

    @Benchmark
    public void plainJdbcReusableFindByIdFetch(Blackhole blackhole) throws Exception {
        plainReusableFindByIdStatement.setLong(1, QUERY_CLIENT_ID);
        try (ResultSet resultSet = plainReusableFindByIdStatement.executeQuery()) {
            List<QBenchmarkClient.FindByIdRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new QBenchmarkClient.FindByIdRow(
                    resultSet.getLong(1),
                    resultSet.getString(2),
                    resultSet.getBoolean(3)
                ));
            }
            blackhole.consume(List.copyOf(rows));
        }
    }

    @Benchmark
    public void plainJdbcReusableFindByIdFetchOptional(Blackhole blackhole) throws Exception {
        plainReusableFindByIdStatement.setLong(1, QUERY_CLIENT_ID);
        try (ResultSet resultSet = plainReusableFindByIdStatement.executeQuery()) {
            Optional<QBenchmarkClient.FindByIdRow> row = Optional.empty();
            if (resultSet.next()) {
                row = Optional.of(new QBenchmarkClient.FindByIdRow(
                    resultSet.getLong(1),
                    resultSet.getString(2),
                    resultSet.getBoolean(3)
                ));
                if (resultSet.next()) {
                    throw new IllegalStateException("expected at most one row");
                }
            }
            blackhole.consume(row);
        }
    }

    @Benchmark
    public void plainJdbcJoinPageFetch(Blackhole blackhole) throws Exception {
        plainJoinPageStatement.setBoolean(1, QUERY_ACTIVE);
        plainJoinPageStatement.setInt(2, PAGE_SIZE);
        plainJoinPageStatement.setInt(3, PAGE_OFFSET);
        try (ResultSet resultSet = plainJoinPageStatement.executeQuery()) {
            List<ClientRouteRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new ClientRouteRow(
                    resultSet.getLong(1),
                    resultSet.getString(2),
                    resultSet.getString(3)
                ));
            }
            blackhole.consume(List.copyOf(rows));
        }
    }

    @Benchmark
    public void mortarJoinPageFetch(Blackhole blackhole) {
        List<ClientRouteRow> rows = mortarClient.fetch(
            mortarJoinPageQuery,
            resultSet -> new ClientRouteRow(resultSet.getLong(1), resultSet.getString(2), resultSet.getString(3))
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void plainJdbcUpdateBatch(Blackhole blackhole) throws Exception {
        plainUpdateStatement.setBoolean(1, false);
        plainUpdateStatement.setLong(2, QUERY_CLIENT_ID);
        plainUpdateStatement.addBatch();
        plainUpdateStatement.setBoolean(1, true);
        plainUpdateStatement.setLong(2, QUERY_CLIENT_ID);
        plainUpdateStatement.addBatch();

        blackhole.consume(plainUpdateStatement.executeBatch());
    }

    @Benchmark
    public void mortarUpdateBatch(Blackhole blackhole) {
        blackhole.consume(mortarClient.executeBatch(mortarUpdateBatch));
    }

    @Benchmark
    public void plainJdbcTunedReusableFindByIdFetch(Blackhole blackhole) throws Exception {
        plainTunedReusableFindByIdStatement.setLong(1, QUERY_CLIENT_ID);
        try (ResultSet resultSet = plainTunedReusableFindByIdStatement.executeQuery()) {
            List<QBenchmarkClient.FindByIdRow> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(new QBenchmarkClient.FindByIdRow(
                    resultSet.getLong(1),
                    resultSet.getString(2),
                    resultSet.getBoolean(3)
                ));
            }
            blackhole.consume(List.copyOf(rows));
        }
    }

    @Benchmark
    public void mortarTunedProcessorGeneratedFindByIdFetch(Blackhole blackhole) {
        List<QBenchmarkClient.FindByIdRow> rows = tunedMortarClient.fetch(
            mortarProcessorGeneratedFindByIdQuery,
            mortarProcessorGeneratedFindByIdParameters
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarJdbcFetch(Blackhole blackhole) {
        List<ClientRow> rows = mortarClient.fetch(
            mortarQuery,
            resultSet -> new ClientRow(resultSet.getLong("id"), resultSet.getString("name"))
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarJdbcFetchOptional(Blackhole blackhole) {
        Optional<ClientRow> row = mortarClient.fetchOptional(
            mortarQuery,
            resultSet -> new ClientRow(resultSet.getLong("id"), resultSet.getString("name"))
        );

        blackhole.consume(row);
    }

    @Benchmark
    public void mortarPreRenderedJdbcFetch(Blackhole blackhole) {
        List<ClientRow> rows = mortarClient.fetch(
            mortarRenderedQuery,
            resultSet -> new ClientRow(resultSet.getLong("id"), resultSet.getString("name"))
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarPreRenderedJdbcFetchOptional(Blackhole blackhole) {
        Optional<ClientRow> row = mortarClient.fetchOptional(
            mortarRenderedQuery,
            resultSet -> new ClientRow(resultSet.getLong("id"), resultSet.getString("name"))
        );

        blackhole.consume(row);
    }

    @Benchmark
    public void mortarGeneratedJdbcFetch(Blackhole blackhole) {
        List<ClientRow> rows = mortarClient.fetch(mortarGeneratedQuery, mortarGeneratedParameters);

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarGeneratedJdbcFetchOptional(Blackhole blackhole) {
        Optional<ClientRow> row = mortarClient.fetchOptional(mortarGeneratedQuery, mortarGeneratedParameters);

        blackhole.consume(row);
    }

    @Benchmark
    public void mortarPreparedGeneratedJdbcFetch(Blackhole blackhole) {
        List<ClientRow> rows = mortarPreparedGeneratedQuery.fetch(mortarGeneratedParameters);

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarPreparedGeneratedJdbcFetchOptional(Blackhole blackhole) {
        Optional<ClientRow> row = mortarPreparedGeneratedQuery.fetchOptional(mortarGeneratedParameters);

        blackhole.consume(row);
    }

    @Benchmark
    public void mortarProcessorGeneratedFindByIdFetch(Blackhole blackhole) {
        List<QBenchmarkClient.FindByIdRow> rows = mortarClient.fetch(
            mortarProcessorGeneratedFindByIdQuery,
            mortarProcessorGeneratedFindByIdParameters
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarProcessorGeneratedFindByIdFetchOptional(Blackhole blackhole) {
        Optional<QBenchmarkClient.FindByIdRow> row = mortarClient.fetchOptional(
            mortarProcessorGeneratedFindByIdQuery,
            mortarProcessorGeneratedFindByIdParameters
        );

        blackhole.consume(row);
    }

    @Benchmark
    public void mortarPreparedProcessorGeneratedFindByIdFetch(Blackhole blackhole) {
        List<QBenchmarkClient.FindByIdRow> rows = mortarPreparedProcessorGeneratedFindByIdQuery.fetch(
            mortarProcessorGeneratedFindByIdParameters
        );

        blackhole.consume(rows);
    }

    @Benchmark
    public void mortarPreparedProcessorGeneratedFindByIdFetchOptional(Blackhole blackhole) {
        Optional<QBenchmarkClient.FindByIdRow> row = mortarPreparedProcessorGeneratedFindByIdQuery.fetchOptional(
            mortarProcessorGeneratedFindByIdParameters
        );

        blackhole.consume(row);
    }

    @Benchmark
    public void jooqFetch(Blackhole blackhole) {
        List<ClientRow> rows = jooq
            .select(jooqId, jooqName)
            .from(jooqClients)
            .where(jooqActive.eq(QUERY_ACTIVE))
            .and(jooqId.eq(QUERY_CLIENT_ID))
            .fetch(record -> new ClientRow(record.get(jooqId), record.get(jooqName)));

        blackhole.consume(rows);
    }

    @Benchmark
    public void jooqFetchOptional(Blackhole blackhole) {
        Optional<ClientRow> row = jooq
            .select(jooqId, jooqName)
            .from(jooqClients)
            .where(jooqActive.eq(QUERY_ACTIVE))
            .and(jooqId.eq(QUERY_CLIENT_ID))
            .fetchOptional(record -> new ClientRow(record.get(jooqId), record.get(jooqName)));

        blackhole.consume(row);
    }

    @Benchmark
    public void querydslFetch(Blackhole blackhole) {
        List<Tuple> tuples = new SQLQuery<Void>(connection, querydslConfiguration)
            .from(querydslClients)
            .select(querydslId, querydslName)
            .where(querydslActive.eq(QUERY_ACTIVE), querydslId.eq(QUERY_CLIENT_ID))
            .fetch();
        List<ClientRow> rows = tuples.stream()
            .map(tuple -> new ClientRow(tuple.get(querydslId), tuple.get(querydslName)))
            .toList();

        blackhole.consume(rows);
    }

    @Benchmark
    public void querydslFetchOptional(Blackhole blackhole) {
        Tuple tuple = new SQLQuery<Void>(connection, querydslConfiguration)
            .from(querydslClients)
            .select(querydslId, querydslName)
            .where(querydslActive.eq(QUERY_ACTIVE), querydslId.eq(QUERY_CLIENT_ID))
            .fetchOne();
        Optional<ClientRow> row = Optional.ofNullable(tuple)
            .map(value -> new ClientRow(value.get(querydslId), value.get(querydslName)));

        blackhole.consume(row);
    }

    static String seedClientName(long id) {
        return String.format(Locale.ROOT, "client-%04d", id);
    }

    private void configureMortar() {
        TableRef clients = new TableRef("clients", "c");
        TableRef routes = new TableRef("routes", "r");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        ColumnRef<Boolean> active = clients.column("active", "active", Boolean.class);
        ColumnRef<Long> routeId = clients.column("routeId", "route_id", Long.class);
        ColumnRef<Long> routeTableId = routes.column("id", "id", Long.class);
        ColumnRef<String> routeName = routes.column("name", "name", String.class);
        mortarQuery = new SimpleMortarDb()
            .from(clients)
            .select(id, name)
            .where(active.eq(QUERY_ACTIVE))
            .where(id.eq(QUERY_CLIENT_ID))
            .build();
        mortarJoinPageQuery = new SimpleMortarDb()
            .from(clients)
            .innerJoin(routes, routeId, routeTableId)
            .select(id, name, routeName)
            .where(active.eq(QUERY_ACTIVE))
            .orderBy(id.asc())
            .limit(PAGE_SIZE)
            .offset(PAGE_OFFSET)
            .build();
        mortarUpdateBatch = List.of(
            new UpdateSpec(clients, List.of(Assignment.of(active, false)), List.of(id.eq(QUERY_CLIENT_ID)), List.of()),
            new UpdateSpec(clients, List.of(Assignment.of(active, true)), List.of(id.eq(QUERY_CLIENT_ID)), List.of())
        );
        PostgresQueryRenderer renderer = new PostgresQueryRenderer();
        mortarRenderedQuery = renderer.render(mortarQuery);
        mortarClient = new MortarJdbcClient(connection, renderer);
        tunedMortarClient = new MortarJdbcClient(tunedConnection, renderer);
        mortarGeneratedQuery = new GeneratedClientLookupQuery();
        mortarGeneratedParameters = new ClientLookupParameters(QUERY_ACTIVE, QUERY_CLIENT_ID);
        mortarPreparedGeneratedQuery = mortarClient.prepare(mortarGeneratedQuery);
        mortarProcessorGeneratedFindByIdQuery = QBenchmarkClient.BENCHMARK_CLIENT.findById(renderer);
        mortarProcessorGeneratedFindByIdParameters = new QBenchmarkClient.FindByIdParameters(QUERY_CLIENT_ID);
        mortarPreparedProcessorGeneratedFindByIdQuery = mortarClient.prepare(mortarProcessorGeneratedFindByIdQuery);
    }

    private void configureJooq() {
        jooq = DSL.using(connection, SQLDialect.POSTGRES);
        jooqClients = DSL.table(DSL.name("clients")).as("c");
        jooqId = DSL.field(DSL.name("c", "id"), Long.class);
        jooqName = DSL.field(DSL.name("c", "name"), String.class);
        jooqActive = DSL.field(DSL.name("c", "active"), Boolean.class);
    }

    private void configureQuerydsl() {
        querydslConfiguration = new Configuration(PostgreSQLTemplates.DEFAULT);
        querydslClients = new RelationalPathBase<>(Object.class, "c", "public", "clients");
        querydslId = Expressions.numberPath(Long.class, querydslClients, "id");
        querydslName = Expressions.stringPath(querydslClients, "name");
        querydslActive = Expressions.booleanPath(querydslClients, "active");
    }

    private void createSchema() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists clients");
            statement.execute("drop table if exists routes");
            statement.execute("""
                create table routes (
                    id bigint primary key,
                    name text not null
                )
                """);
            statement.execute("""
                create table clients (
                    id bigint primary key,
                    route_id bigint not null references routes(id),
                    name text not null,
                    active boolean not null
                )
                """);
            statement.execute("create index clients_active_id_idx on clients (active, id)");
        }
    }

    private void seedClients() throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
            "insert into routes (id, name) values (?, ?)"
        )) {
            for (long id = 1L; id <= 10L; id++) {
                statement.setLong(1, id);
                statement.setString(2, "route-" + id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "insert into clients (id, route_id, name, active) values (?, ?, ?, ?)"
        )) {
            for (long id = 1L; id <= DATASET_SIZE; id++) {
                statement.setLong(1, id);
                statement.setLong(2, (id % 10L) + 1L);
                statement.setString(3, seedClientName(id));
                statement.setBoolean(4, id % 2L != 0L);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        plainReusableStatement = connection.prepareStatement(SELECT_SQL);
        plainReusableFindByIdStatement = connection.prepareStatement(SELECT_BY_ID_SQL);
        plainJoinPageStatement = connection.prepareStatement(SELECT_JOIN_PAGE_SQL);
        plainUpdateStatement = connection.prepareStatement(UPDATE_SQL);
        plainTunedReusableFindByIdStatement = tunedConnection.prepareStatement(SELECT_BY_ID_SQL);
    }

    private String tunedJdbcUrl() {
        String separator = postgres.getJdbcUrl().contains("?") ? "&" : "?";
        return postgres.getJdbcUrl() + separator + TUNED_PGJDBC_PARAMETERS;
    }

    private record ClientLookupParameters(boolean active, long id) {
    }

    private record ClientRow(Long id, String name) {
    }

    private record ClientRouteRow(Long id, String name, String routeName) {
    }

    private static final class GeneratedClientLookupQuery implements MortarGeneratedQuery<ClientLookupParameters, ClientRow> {
        private static final TableRef CLIENTS = new TableRef("clients", "c");
        private static final ColumnRef<Long> ID = CLIENTS.column("id", "id", Long.class);
        private static final ColumnRef<String> NAME = CLIENTS.column("name", "name", String.class);
        private static final ColumnRef<Boolean> ACTIVE = CLIENTS.column("active", "active", Boolean.class);
        private static final List<TableRef> TABLES = List.of(CLIENTS);
        private static final List<ColumnRef<?>> COLUMNS = List.of(ID, NAME, ACTIVE);
        private static final QueryMetadata METADATA = new QueryMetadata(TABLES, COLUMNS, List.of());

        @Override
        public String sql() {
            return SELECT_SQL;
        }

        @Override
        public List<Class<?>> parameterTypes() {
            return List.of(Boolean.class, Long.class);
        }

        @Override
        public QueryMetadata metadata() {
            return METADATA;
        }

        @Override
        public void bind(PreparedStatement statement, ClientLookupParameters parameters) throws java.sql.SQLException {
            statement.setBoolean(1, parameters.active());
            statement.setLong(2, parameters.id());
        }

        @Override
        public ClientRow map(ResultSet resultSet) throws java.sql.SQLException {
            return new ClientRow(resultSet.getLong(1), resultSet.getString(2));
        }
    }
}
