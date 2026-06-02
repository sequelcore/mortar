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

final class MortarProcessorSourceMapContractFixtureTest {
    @TempDir
    private Path tempDir;

    @Test
    void emitsTheSharedR18SourceMapContractFixture() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Path classDir = tempDir.resolve("classes");
        Path generatedDir = tempDir.resolve("generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path accountSource = sourceDir.resolve("example").resolve("Account.java");
        Files.writeString(accountSource, """
            package example;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "accounts", alias = "a")
            final class Account {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name")
                String name;
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

                @MortarColumn(name = "name")
                String name;

                @MortarRelation(target = Account.class, localColumn = "account_id", targetColumn = "id", nullable = false)
                Account account;
            }
            """, StandardCharsets.UTF_8);

        compile(classDir, generatedDir, accountSource, clientSource);

        assertThat(read(classDir.resolve("META-INF/mortar/entities.json")))
            .isEqualTo(read(fixture("entities.json")));
        assertThat(read(classDir.resolve("META-INF/mortar/source-map.json")))
            .isEqualTo(read(fixture("source-map.json")));
    }

    private static void compile(Path classDir, Path generatedDir, Path... sourceFiles) throws Exception {
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
                fileManager.getJavaFileObjectsFromPaths(List.of(sourceFiles))
            ).call();

            assertThat(compiled).isTrue();
        }
    }

    private static Path fixture(String fileName) {
        return repositoryRoot()
            .resolve("rust")
            .resolve("crates")
            .resolve("mortar-compiler")
            .resolve("test-fixtures")
            .resolve("source-map-contract")
            .resolve("r18")
            .resolve(fileName);
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                && Files.exists(current.resolve("CLAUDE.md"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate Mortar repository root");
    }

    private static String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
