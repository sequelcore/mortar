package dev.mortar.benchmarks;

import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberPath;
import com.querydsl.core.types.dsl.StringPath;
import com.querydsl.sql.Configuration;
import com.querydsl.sql.PostgreSQLTemplates;
import com.querydsl.sql.RelationalPathBase;
import com.querydsl.sql.SQLQuery;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.impl.DSL;
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
public class ReferenceRenderingBenchmark {
    private DSLContext jooq;
    private Field<Long> jooqId;
    private Field<String> jooqName;
    private Configuration querydslConfiguration;
    private RelationalPathBase<Object> querydslClients;
    private NumberPath<Long> querydslId;
    private StringPath querydslName;

    @Setup
    public void setUp() {
        jooq = DSL.using(SQLDialect.POSTGRES);
        jooqId = DSL.field(DSL.name("c", "id"), Long.class);
        jooqName = DSL.field(DSL.name("c", "name"), String.class);
        querydslConfiguration = new Configuration(PostgreSQLTemplates.DEFAULT);
        querydslClients = new RelationalPathBase<>(Object.class, "c", "public", "clients");
        querydslId = Expressions.numberPath(Long.class, querydslClients, "id");
        querydslName = Expressions.stringPath(querydslClients, "name");
    }

    @Benchmark
    public void jooqRenderSelectQuery(Blackhole blackhole) {
        String sql = jooq
            .select(jooqId, jooqName)
            .from(DSL.table(DSL.name("clients")).as("c"))
            .where(jooqName.likeIgnoreCase("%ada%"))
            .and(jooqId.ge(10L))
            .orderBy(jooqName.asc())
            .limit(25)
            .offset(50)
            .getSQL(ParamType.INDEXED);

        blackhole.consume(sql);
    }

    @Benchmark
    public void querydslRenderSelectQuery(Blackhole blackhole) {
        String sql = new SQLQuery<Void>(querydslConfiguration)
            .from(querydslClients)
            .select(querydslId, querydslName)
            .where(querydslName.containsIgnoreCase("ada"), querydslId.goe(10L))
            .orderBy(querydslName.asc())
            .limit(25)
            .offset(50)
            .getSQL()
            .getSQL();

        blackhole.consume(sql);
    }
}
