package dev.mortar.benchmarks;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.Parameter;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.jdbc.MortarJdbcClient;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(2)
@State(Scope.Thread)
public class JdbcExecutionBenchmark {
    private static final String SQL = "select id, name from clients where id = ?";

    private DataSource dataSource;
    private MortarJdbcClient mortarClient;
    private QuerySpec query;

    @Setup
    public void setUp() {
        dataSource = fakeDataSource();
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        query = new SimpleMortarDb()
            .from(clients)
            .where(id.eq(7L))
            .build();
        mortarClient = new MortarJdbcClient(
            dataSource,
            ignored -> new RenderedQuery(SQL, List.of(Parameter.of(7L)))
        );
    }

    @Benchmark
    public void plainJdbcFetch(Blackhole blackhole) throws Exception {
        try (
            Connection connection = dataSource.getConnection();
            PreparedStatement statement = connection.prepareStatement(SQL)
        ) {
            statement.setLong(1, 7L);
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
    public void mortarJdbcFetch(Blackhole blackhole) {
        List<ClientRow> rows = mortarClient.fetch(
            query,
            resultSet -> new ClientRow(resultSet.getLong("id"), resultSet.getString("name"))
        );

        blackhole.consume(rows);
    }

    private static DataSource fakeDataSource() {
        return (DataSource) Proxy.newProxyInstance(
            JdbcExecutionBenchmark.class.getClassLoader(),
            new Class<?>[] { DataSource.class },
            (proxy, method, args) -> {
                if (method.getName().equals("getConnection")) {
                    return fakeConnection();
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static Connection fakeConnection() {
        return (Connection) Proxy.newProxyInstance(
            JdbcExecutionBenchmark.class.getClassLoader(),
            new Class<?>[] { Connection.class },
            (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    return fakeStatement();
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static PreparedStatement fakeStatement() {
        return (PreparedStatement) Proxy.newProxyInstance(
            JdbcExecutionBenchmark.class.getClassLoader(),
            new Class<?>[] { PreparedStatement.class },
            (InvocationHandler) (proxy, method, args) -> {
                if (method.getName().equals("executeQuery")) {
                    return fakeResultSet();
                }
                return defaultValue(method.getReturnType());
            }
        );
    }

    private static ResultSet fakeResultSet() {
        return (ResultSet) Proxy.newProxyInstance(
            JdbcExecutionBenchmark.class.getClassLoader(),
            new Class<?>[] { ResultSet.class },
            new InvocationHandler() {
                private boolean unread = true;

                @Override
                public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
                    if (method.getName().equals("next")) {
                        boolean current = unread;
                        unread = false;
                        return current;
                    }
                    if (method.getName().equals("getLong")) {
                        return 7L;
                    }
                    if (method.getName().equals("getString")) {
                        return "Ada";
                    }
                    return defaultValue(method.getReturnType());
                }
            }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type.equals(boolean.class)) {
            return false;
        }
        if (type.equals(int.class)) {
            return 0;
        }
        if (type.equals(long.class)) {
            return 0L;
        }
        return null;
    }

    private record ClientRow(long id, String name) {
    }
}
