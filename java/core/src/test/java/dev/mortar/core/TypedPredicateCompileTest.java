package dev.mortar.core;

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

final class TypedPredicateCompileTest {
    @TempDir
    private Path tempDir;

    @Test
    void rejectsWrongPredicateValueTypeAtCompileTime() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.core.ColumnRef;
            import dev.mortar.core.TableRef;

            final class InvalidPredicate {
                void query() {
                    TableRef clients = new TableRef("clients", "c");
                    ColumnRef<Long> id = clients.column("id", "id", Long.class);
                    id.eq("not-a-long");
                }
            }
            """);

        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("incompatible types"));
    }

    @Test
    void acceptsCorrectPredicateValueTypeAtCompileTime() throws Exception {
        CompilationResult result = compile("""
            package example;

            import dev.mortar.core.ColumnRef;
            import dev.mortar.core.TableRef;

            final class ValidPredicate {
                void query() {
                    TableRef clients = new TableRef("clients", "c");
                    ColumnRef<Long> id = clients.column("id", "id", Long.class);
                    id.eq(42L);
                }
            }
            """);

        assertThat(result.compiled()).isTrue();
    }

    private CompilationResult compile(String source) throws Exception {
        Path sourceDir = tempDir.resolve("source-" + Math.abs(source.hashCode()));
        Path classDir = tempDir.resolve("classes-" + Math.abs(source.hashCode()));
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);

        Path sourceFile = sourceDir.resolve("example").resolve("PredicateSample.java");
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));

            boolean compiled = compiler.getTask(
                null,
                fileManager,
                diagnostics,
                List.of("-classpath", System.getProperty("java.class.path")),
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
