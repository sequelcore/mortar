package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class MortarProcessorGenerationTest {
    @TempDir
    private Path tempDir;

    @Test
    void generatesQClassForAnnotatedEntity() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Path classDir = tempDir.resolve("classes");
        Path generatedDir = tempDir.resolve("generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients", alias = "c")
            final class Client {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name")
                String name;
            }
            """, StandardCharsets.UTF_8);
        Path usageSource = sourceDir.resolve("example").resolve("ClientUsage.java");
        Files.writeString(usageSource, """
            package example;

            import dev.mortar.core.QueryRenderer;
            import dev.mortar.jdbc.MortarJdbcClient;
            import java.util.Optional;

            final class ClientUsage {
                Optional<QClient.FindByIdRow> findById(MortarJdbcClient jdbcClient, QueryRenderer renderer, Long id) {
                    return jdbcClient.fetchOptional(QClient.CLIENT.findById(renderer), new QClient.FindByIdParameters(id));
                }
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(clientSource, usageSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        Path generatedSource = generatedDir.resolve("example").resolve("QClient.java");
        assertThat(Files.readString(generatedSource, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("public final class QClient implements MortarTable")
            .contains("public static final QClient CLIENT = new QClient();")
            .contains("public final TableRef table = new TableRef(\"clients\", \"c\");")
            .contains("public final ColumnRef<java.lang.Long> id = table.column(\"id\", \"id\", java.lang.Long.class);")
            .contains("public final ColumnRef<java.lang.String> name = table.column(\"name\", \"name\", java.lang.String.class);")
            .contains("public FindByIdQuery findById(dev.mortar.core.QueryRenderer renderer)")
            .contains("public static final class FindByIdQuery implements MortarGeneratedQuery<FindByIdParameters, FindByIdRow>");
    }

    @Test
    void generatesFindByIdExecutorForAnnotatedEntity() throws Exception {
        Path sourceDir = tempDir.resolve("executor-source");
        Path classDir = tempDir.resolve("executor-classes");
        Path generatedDir = tempDir.resolve("executor-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients", alias = "c")
            final class Client {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name")
                String name;

                @MortarColumn(name = "active")
                Boolean active;
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(clientSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        String generatedSource = Files.readString(
            generatedDir.resolve("example").resolve("QClient.java"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertThat(generatedSource)
            .contains("import dev.mortar.jdbc.MortarGeneratedQuery;")
            .contains("Generated primary-key lookup for SQL table {@code clients}.")
            .contains("SQL is rendered by the supplied dialect renderer and executed only by a runtime adapter.")
            .contains("Parameters for the generated primary-key lookup.")
            .contains("Row returned by the generated fixed read.")
            .contains("public FindByIdQuery findById(dev.mortar.core.QueryRenderer renderer)")
            .contains("public record FindByIdParameters(java.lang.Long id)")
            .contains("public record FindByIdRow(java.lang.Long id, java.lang.String name, java.lang.Boolean active)")
            .contains("public static final class FindByIdQuery implements MortarGeneratedQuery<FindByIdParameters, FindByIdRow>")
            .contains("this.renderedQuery = java.util.Objects.requireNonNull(renderer, \"renderer cannot be null\").render(findByIdSpec());")
            .contains("return renderedQuery.sql();")
            .contains("return java.util.List.of(java.lang.Long.class);")
            .contains("statement.setLong(1, parameters.id());")
            .contains("return new FindByIdRow(readLong(resultSet, 1), resultSet.getString(2), readBoolean(resultSet, 3));");
    }

    @Test
    void generatesFindAllExecutorForAnnotatedEntity() throws Exception {
        Path sourceDir = tempDir.resolve("find-all-source");
        Path classDir = tempDir.resolve("find-all-classes");
        Path generatedDir = tempDir.resolve("find-all-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients", alias = "c")
            final class Client {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name")
                String name;

                @MortarColumn(name = "active")
                Boolean active;
            }
            """, StandardCharsets.UTF_8);
        Path usageSource = sourceDir.resolve("example").resolve("ClientUsage.java");
        Files.writeString(usageSource, """
            package example;

            import dev.mortar.core.QueryRenderer;
            import dev.mortar.jdbc.MortarJdbcClient;
            import java.util.List;

            final class ClientUsage {
                List<QClient.FindAllRow> findAll(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
                    return jdbcClient.fetch(QClient.CLIENT.findAll(renderer));
                }
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(clientSource, usageSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        String generatedSource = Files.readString(
            generatedDir.resolve("example").resolve("QClient.java"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertThat(generatedSource)
            .contains("public FindAllQuery findAll(dev.mortar.core.QueryRenderer renderer)")
            .contains("Generated query for {@code select id, name, active from clients}.")
            .contains("SQL is rendered by the supplied dialect renderer and executed only by a runtime adapter.")
            .contains("Row returned by the generated fixed read.")
            .doesNotContain("public record FindAllParameters()")
            .contains("public record FindAllRow(java.lang.Long id, java.lang.String name, java.lang.Boolean active)")
            .contains("public static final class FindAllQuery implements MortarGeneratedQuery<dev.mortar.jdbc.MortarNoParameters, FindAllRow>")
            .contains("return java.util.List.of();")
            .contains("public void bind(java.sql.PreparedStatement statement, dev.mortar.jdbc.MortarNoParameters parameters) throws java.sql.SQLException")
            .contains("java.util.Objects.requireNonNull(parameters, \"parameters cannot be null\");")
            .contains("return new FindAllRow(readLong(resultSet, 1), resultSet.getString(2), readBoolean(resultSet, 3));")
            .contains("private static dev.mortar.core.QuerySpec findAllSpec()");
    }

    @Test
    void generatesR16ReadFacadeNamespaceForFixedSingleTableReads() throws Exception {
        Path sourceDir = tempDir.resolve("r16-guard-source");
        Path classDir = tempDir.resolve("r16-guard-classes");
        Path generatedDir = tempDir.resolve("r16-guard-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients", alias = "c")
            final class Client {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name")
                String name;

                @MortarColumn(name = "active")
                Boolean active;
            }
            """, StandardCharsets.UTF_8);
        Path usageSource = sourceDir.resolve("example").resolve("ClientUsage.java");
        Files.writeString(usageSource, """
            package example;

            import dev.mortar.core.MortarBoundQuery;
            import dev.mortar.core.QueryRenderer;
            import dev.mortar.jdbc.MortarJdbcClient;
            import java.util.List;
            import java.util.Optional;

            final class ClientUsage {
                MortarBoundQuery<QClient.FindByIdRow> findByIdQuery(QueryRenderer renderer, Long id) {
                    return QClient.CLIENT.read(renderer)
                        .findById(id)
                        .named("ClientUsage.findById");
                }

                Optional<QClient.FindByIdRow> findById(MortarJdbcClient jdbcClient, QueryRenderer renderer, Long id) {
                    return jdbcClient.fetchOptional(
                        QClient.CLIENT.read(renderer)
                            .findById(id)
                            .named("ClientUsage.findById")
                    );
                }

                List<QClient.FindAllRow> findAll(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
                    return jdbcClient.fetch(
                        QClient.CLIENT.read(renderer)
                            .findAll()
                            .named("ClientUsage.findAll")
                    );
                }
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(clientSource, usageSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        String generatedSource = Files.readString(
            generatedDir.resolve("example").resolve("QClient.java"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");

        assertThat(generatedSource)
            .contains("Creates a fixed single-table read facade for rendered, inspectable queries.")
            .contains("The returned facade does not execute SQL; pass its bound queries to a runtime adapter.")
            .contains("public Read read(dev.mortar.core.QueryRenderer renderer)")
            .contains("Generated fixed-read namespace for this table.")
            .contains("public static final class Read")
            .contains("Renders a primary-key lookup as an inspectable bound query.")
            .contains("The query is not executed until a runtime adapter receives it.")
            .contains("public dev.mortar.core.MortarBoundQuery<FindByIdRow> findById(java.lang.Long id)")
            .contains("return dev.mortar.core.MortarBoundQuery.unnamed(renderer.render(findByIdSpec(id)), FindByIdRow.class);")
            .contains("Renders an explicit full-table read for all mapped columns.")
            .contains("public dev.mortar.core.MortarBoundQuery<FindAllRow> findAll()")
            .contains("return dev.mortar.core.MortarBoundQuery.unnamed(renderer.render(findAllSpec()), FindAllRow.class);")
            .doesNotContain("projectRecord(")
            .doesNotContain("projectDto(")
            .doesNotContain("public Write")
            .doesNotContain(" write(")
            .doesNotContain(" fetch(")
            .doesNotContain(" fetchOptional(")
            .doesNotContain(" execute(")
            .doesNotContain(" count(")
            .doesNotContain(" exists(");
    }

    @Test
    void generatesQClassForAnnotatedRecord() throws Exception {
        Path sourceDir = tempDir.resolve("record-source");
        Path classDir = tempDir.resolve("record-classes");
        Path generatedDir = tempDir.resolve("record-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path clientSource = sourceDir.resolve("example").resolve("ClientRow.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "client_rows", alias = "cr")
            record ClientRow(
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id,

                @MortarColumn(name = "name")
                String name
            ) {
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(clientSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        Path generatedSource = generatedDir.resolve("example").resolve("QClientRow.java");
        assertThat(Files.readString(generatedSource, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("public final class QClientRow implements MortarTable")
            .contains("public static final QClientRow CLIENT_ROW = new QClientRow();")
            .contains("public final TableRef table = new TableRef(\"client_rows\", \"cr\");")
            .contains("public final ColumnRef<java.lang.Long> id = table.column(\"id\", \"id\", java.lang.Long.class);")
            .contains("public final ColumnRef<java.lang.String> name = table.column(\"name\", \"name\", java.lang.String.class);")
            .contains("public FindByIdQuery findById(dev.mortar.core.QueryRenderer renderer)")
            .contains("public static final class FindByIdQuery implements MortarGeneratedQuery<FindByIdParameters, FindByIdRow>");
    }

    @Test
    void generatesQClassFromJpaAnnotationsWithoutJakartaDependency() throws Exception {
        Path sourceDir = tempDir.resolve("jpa-source");
        Path classDir = tempDir.resolve("jpa-classes");
        Path generatedDir = tempDir.resolve("jpa-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(sourceDir.resolve("jakarta").resolve("persistence"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        writeJpaAnnotation(sourceDir, "Entity", """
            package jakarta.persistence;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Entity {
            }
            """);
        writeJpaAnnotation(sourceDir, "Table", """
            package jakarta.persistence;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Table {
                String name();
            }
            """);
        writeJpaAnnotation(sourceDir, "Column", """
            package jakarta.persistence;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.FIELD)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Column {
                String name();
                boolean nullable() default true;
            }
            """);
        writeJpaAnnotation(sourceDir, "Id", """
            package jakarta.persistence;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Retention;
            import java.lang.annotation.RetentionPolicy;
            import java.lang.annotation.Target;

            @Target(ElementType.FIELD)
            @Retention(RetentionPolicy.RUNTIME)
            public @interface Id {
            }
            """);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import jakarta.persistence.Column;
            import jakarta.persistence.Entity;
            import jakarta.persistence.Id;
            import jakarta.persistence.Table;

            @Entity
            @Table(name = "clients")
            final class Client {
                @Id
                @Column(name = "id", nullable = false)
                Long id;

                @Column(name = "name")
                String name;
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(
                    sourceDir.resolve("jakarta").resolve("persistence").resolve("Entity.java"),
                    sourceDir.resolve("jakarta").resolve("persistence").resolve("Table.java"),
                    sourceDir.resolve("jakarta").resolve("persistence").resolve("Column.java"),
                    sourceDir.resolve("jakarta").resolve("persistence").resolve("Id.java"),
                    clientSource
                ))
            ).call();

            assertThat(compiled).isTrue();
        }

        Path generatedSource = generatedDir.resolve("example").resolve("QClient.java");
        assertThat(Files.readString(generatedSource, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("Mortar metamodel for SQL table {@code clients} using alias {@code c}.")
            .contains("public final class QClient implements MortarTable")
            .contains("public final TableRef table = new TableRef(\"clients\", \"c\");")
            .contains("public final ColumnRef<java.lang.Long> id = table.column(\"id\", \"id\", java.lang.Long.class);")
            .contains("public final ColumnRef<java.lang.String> name = table.column(\"name\", \"name\", java.lang.String.class);");
    }

    @Test
    void generatesRelationRefsForAnnotatedRelations() throws Exception {
        Path sourceDir = tempDir.resolve("relation-source");
        Path classDir = tempDir.resolve("relation-classes");
        Path generatedDir = tempDir.resolve("relation-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path routeSource = sourceDir.resolve("example").resolve("Route.java");
        Files.writeString(routeSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "routes", alias = "r")
            final class Route {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;
            }
            """, StandardCharsets.UTF_8);

        Path clientSource = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(clientSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;
            import dev.mortar.processor.MortarRelation;

            @MortarEntity(table = "clients", alias = "c")
            final class Client {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarRelation(target = Route.class, localColumn = "route_id")
                Route route;
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                null,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(routeSource, clientSource))
            ).call();

            assertThat(compiled).isTrue();
        }

        Path generatedSource = generatedDir.resolve("example").resolve("QClient.java");
        assertThat(Files.readString(generatedSource, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("import dev.mortar.core.RelationRef;")
            .contains("public final RelationRef route = new RelationRef(")
            .contains("\"route\",")
            .contains("new TableRef(\"routes\", \"r\"),")
            .contains("table.column(\"route\", \"route_id\", java.lang.Object.class),")
            .contains("new TableRef(\"routes\", \"r\").column(\"id\", \"id\", java.lang.Object.class),")
            .contains("true");
    }

    private void writeJpaAnnotation(Path sourceDir, String name, String source) throws Exception {
        Files.writeString(
            sourceDir.resolve("jakarta").resolve("persistence").resolve(name + ".java"),
            source,
            StandardCharsets.UTF_8
        );
    }
}
