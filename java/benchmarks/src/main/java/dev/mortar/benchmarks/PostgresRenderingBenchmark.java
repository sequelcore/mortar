package dev.mortar.benchmarks;

import dev.mortar.core.ColumnRef;
import dev.mortar.core.QuerySpec;
import dev.mortar.core.RenderedQuery;
import dev.mortar.core.SimpleMortarDb;
import dev.mortar.core.TableRef;
import dev.mortar.postgres.PostgresQueryRenderer;

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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(2)
@State(Scope.Thread)
public class PostgresRenderingBenchmark {
    private PostgresQueryRenderer renderer;
    private QuerySpec query;

    @Setup
    public void setUp() {
        TableRef clients = new TableRef("clients", "c");
        ColumnRef<Long> id = clients.column("id", "id", Long.class);
        ColumnRef<String> name = clients.column("name", "name", String.class);
        renderer = new PostgresQueryRenderer();
        query = new SimpleMortarDb()
            .from(clients)
            .select(id, name)
            .where(name.containsIgnoreCase("ada"))
            .where(id.gte(10L))
            .orderBy(name.asc())
            .limit(25)
            .offset(50)
            .build();
    }

    @Benchmark
    public void renderSelectQuery(Blackhole blackhole) {
        RenderedQuery renderedQuery = renderer.render(query);

        blackhole.consume(renderedQuery.sql());
        blackhole.consume(renderedQuery.parameters());
        blackhole.consume(renderedQuery.metadata());
    }
}
