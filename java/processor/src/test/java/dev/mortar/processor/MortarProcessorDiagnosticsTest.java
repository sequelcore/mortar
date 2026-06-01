package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

final class MortarProcessorDiagnosticsTest {
    @TempDir
    private Path tempDir;

    @Test
    void rejectsEntityWithoutId() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;

            @MortarEntity(table = "clients")
            final class Client {
                @MortarColumn(name = "name")
                String name;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_001"));
    }

    @Test
    void rejectsDuplicateColumns() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients")
            final class Client {
                @MortarId
                @MortarColumn(name = "id")
                Long id;

                @MortarColumn(name = "id")
                String externalId;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_002"));
    }

    @Test
    void rejectsUnsupportedGenericColumnType() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;
            import java.util.List;

            @MortarEntity(table = "clients")
            final class Client {
                @MortarId
                @MortarColumn(name = "id")
                Long id;

                @MortarColumn(name = "tags")
                List<String> tags;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_003"));
    }

    @Test
    void rejectsAmbiguousRelationWithoutLocalColumn() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;
            import dev.mortar.processor.MortarRelation;

            @MortarEntity(table = "routes")
            final class Route {
                @MortarId
                @MortarColumn(name = "id")
                Long id;
            }

            @MortarEntity(table = "clients")
            final class Client {
                @MortarId
                @MortarColumn(name = "id")
                Long id;

                @MortarRelation(target = Route.class, localColumn = " ")
                Route route;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_004"));
    }

    @Test
    void rejectsBlankRelationTargetColumn() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;
            import dev.mortar.processor.MortarRelation;

            @MortarEntity(table = "routes")
            final class Route {
                @MortarId
                @MortarColumn(name = "id")
                Long id;
            }

            @MortarEntity(table = "clients")
            final class Client {
                @MortarId
                @MortarColumn(name = "id")
                Long id;

                @MortarRelation(target = Route.class, localColumn = "route_id", targetColumn = " ")
                Route route;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_007"));
    }

    @Test
    void rejectsInvalidSqlMetadata() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "clients;drop", alias = " ")
            final class Client {
                @MortarId
                @MortarColumn(name = "id")
                Long id;

                @MortarColumn(name = "display-name")
                String name;
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_005"));
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_006"));
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_007"));
    }

    private CompilationResult compile(String source) throws Exception {
        Path sourceDir = tempDir.resolve("source-" + Math.abs(source.hashCode()));
        Path classDir = tempDir.resolve("classes-" + Math.abs(source.hashCode()));
        Path generatedDir = tempDir.resolve("generated-" + Math.abs(source.hashCode()));
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path sourceFile = sourceDir.resolve("example").resolve("Client.java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of(
                    "-classpath",
                    System.getProperty("java.class.path"),
                    "-processor",
                    MortarProcessor.class.getName()
                ),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile))
            ).call();

            List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .toList();
            return new CompilationResult(compiled, errors);
        }
    }

    private record CompilationResult(boolean compiled, List<String> errors) {
    }
}
