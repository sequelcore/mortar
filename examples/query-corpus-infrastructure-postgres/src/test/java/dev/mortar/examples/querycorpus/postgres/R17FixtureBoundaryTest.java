package dev.mortar.examples.querycorpus.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class R17FixtureBoundaryTest {
    @Test
    void domainAndApplicationSourcesDoNotImportMortarTypes() throws IOException {
        List<Path> sourceRoots = List.of(
            Path.of("..", "query-corpus-domain", "src", "main", "java"),
            Path.of("..", "query-corpus-domain", "src", "test", "java"),
            Path.of("..", "query-corpus-application", "src", "main", "java"),
            Path.of("..", "query-corpus-application", "src", "test", "java")
        );

        List<Path> leakingSources = sourceRoots.stream()
            .flatMap(root -> javaFiles(root).stream())
            .filter(R17FixtureBoundaryTest::importsMortarApi)
            .toList();

        assertThat(leakingSources).isEmpty();
    }

    @Test
    void domainAndApplicationModulesDoNotDependOnMortarModules() throws IOException {
        List<Path> buildFiles = List.of(
            Path.of("..", "query-corpus-domain", "build.gradle.kts"),
            Path.of("..", "query-corpus-application", "build.gradle.kts")
        );

        List<Path> leakingBuildFiles = buildFiles.stream()
            .filter(R17FixtureBoundaryTest::mentionsMortarModuleDependency)
            .toList();

        assertThat(leakingBuildFiles).isEmpty();
    }

    @Test
    void r18SnapshotInventoryContainsStableCorpusKeys() throws IOException {
        String snapshots = Files.readString(
            Path.of("src", "test", "resources", "r17-query-corpus", "mortar.sql.snap.json"),
            StandardCharsets.UTF_8
        );

        assertThat(snapshots)
            .contains("\"r17.ticket.header-by-id\"")
            .contains("\"r17.ticket.status-options\"")
            .contains("\"r17.ticket.search\"")
            .contains("\"r17.ticket.open-region-page\"")
            .contains("\"r17.ticket.unassigned-critical-page\"")
            .contains("\"r17.ticket.detail\"")
            .contains("\"TicketReader.findHeader\"")
            .contains("\"TicketReader.searchTickets\"");
    }

    private static List<Path> javaFiles(Path root) {
        try (var paths = Files.walk(root)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan " + root, exception);
        }
    }

    private static boolean importsMortarApi(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains("dev.mortar.core.")
                || source.contains("dev.mortar.jdbc.")
                || source.contains("dev.mortar.postgres.")
                || source.contains("dev.mortar.processor.")
                || source.contains("dev.mortar.testkit.")
                || source.contains("dev.mortar.examples.querycorpus.postgres.")
                || source.contains("import dev.mortar.core.")
                || source.contains("import dev.mortar.jdbc.")
                || source.contains("import dev.mortar.postgres.")
                || source.contains("import dev.mortar.processor.")
                || source.contains("import dev.mortar.testkit.")
                || source.contains("import dev.mortar.examples.querycorpus.postgres.");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static boolean mentionsMortarModuleDependency(Path path) {
        try {
            String source = Files.readString(path, StandardCharsets.UTF_8);
            return source.contains(":java:core")
                || source.contains(":java:dialect-postgres")
                || source.contains(":java:runtime-jdbc")
                || source.contains(":java:processor")
                || source.contains(":java:testkit")
                || source.contains(":examples:query-corpus-infrastructure-postgres");
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }
}
