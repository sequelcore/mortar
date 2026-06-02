package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import dev.mortar.core.MortarBoundQuery;
import dev.mortar.jdbc.MortarGeneratedQuery;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MortarProcessorGradleIncrementalConvergenceTest {
    private static final Pattern QUERY_FINGERPRINT = Pattern.compile(
        "\"id\"\\s*:\\s*\"([^\"]+)\"(?s:.*?)\"fingerprint\"\\s*:\\s*\"(sha256:[^\"]+)\""
    );

    @TempDir
    private Path tempDir;

    @Test
    void cleanAndIncrementalBuildsConvergeForR17StyleMultiModuleSourceMapInventory() throws Exception {
        Path projectDir = tempDir.resolve("fixture");
        writeFixtureProject(projectDir);
        assertDomainAndApplicationAreMortarFree(projectDir);

        runGradle(projectDir, ":infrastructure:compileJava", "--info");
        String baselineSourceMap = sourceMap(projectDir);
        assertThat(baselineSourceMap)
            .contains("\"id\": \"example.r17.TicketRecord.findById\"")
            .contains("\"id\": \"example.r17.TicketStatusRecord.findAll\"")
            .contains("\"generated_member\": \"read.findById\"")
            .contains("\"generated_member\": \"read.findAll\"");

        String baselineFingerprint = fingerprint(baselineSourceMap, "example.r17.TicketRecord.findById");

        writeInfrastructureSources(projectDir, "subject", "QTicketRecord.TICKET_RECORD.subject.eq(\"Router outage\");");
        runGradle(projectDir, ":infrastructure:compileJava", "--info");

        String incrementalMetadata = metadata(projectDir);
        String incrementalSourceMap = sourceMap(projectDir);
        assertThat(incrementalMetadata)
            .contains("\"property\": \"subject\"")
            .doesNotContain("\"property\": \"summary\"");
        assertThat(incrementalSourceMap)
            .contains("\"id\": \"example.r17.TicketRecord.findById\"")
            .contains("\"java_type\": \"example.r17.TicketRecord\"");
        assertThat(fingerprint(incrementalSourceMap, "example.r17.TicketRecord.findById"))
            .isNotEqualTo(baselineFingerprint);

        Map<String, String> incrementalInventory = sourceMapInventory(incrementalSourceMap);
        runGradle(projectDir, ":infrastructure:clean", ":infrastructure:compileJava", "--info");
        assertThat(sourceMapInventory(sourceMap(projectDir))).isEqualTo(incrementalInventory);

        deleteInfrastructureEntities(projectDir);
        runGradle(projectDir, ":infrastructure:compileJava", "--info");

        assertThat(metadata(projectDir))
            .contains("\"entities\": []")
            .doesNotContain("example.r17.TicketRecord.findById")
            .doesNotContain("example.r17.TicketStatusRecord.findAll");
        assertThat(sourceMap(projectDir))
            .contains("\"queries\": []")
            .doesNotContain("example.r17.TicketRecord.findById")
            .doesNotContain("example.r17.TicketStatusRecord.findAll");
    }

    private static void writeFixtureProject(Path projectDir) throws Exception {
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("settings.gradle"), """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenCentral()
                }
            }

            rootProject.name = 'mortar-r18-fixture'
            include 'domain', 'application', 'infrastructure'
            """, StandardCharsets.UTF_8);
        Files.writeString(projectDir.resolve("build.gradle"), """
            subprojects {
                apply plugin: 'java-library'

                java {
                    toolchain {
                        languageVersion = JavaLanguageVersion.of(21)
                    }
                }

                tasks.withType(JavaCompile).configureEach {
                    options.encoding = 'UTF-8'
                    options.compilerArgs.addAll(['-Xlint:all', '-Werror', '-parameters'])
                }
            }
            """, StandardCharsets.UTF_8);

        Path domainDir = projectDir.resolve("domain");
        Path applicationDir = projectDir.resolve("application");
        Path infrastructureDir = projectDir.resolve("infrastructure");
        Files.createDirectories(domainDir);
        Files.createDirectories(applicationDir);
        Files.createDirectories(infrastructureDir);
        Files.writeString(domainDir.resolve("build.gradle"), "", StandardCharsets.UTF_8);
        Files.writeString(applicationDir.resolve("build.gradle"), """
            dependencies {
                implementation project(':domain')
            }
            """, StandardCharsets.UTF_8);
        Files.writeString(infrastructureDir.resolve("build.gradle"), """
            dependencies {
                implementation project(':application')
                implementation project(':domain')
                implementation files('%s')
                implementation files('%s')
                compileOnly files('%s', '%s')
                annotationProcessor files('%s', '%s')
            }

            tasks.withType(JavaCompile).configureEach {
                options.compilerArgs.add('-Xlint:-processing')
            }
            """.formatted(
            gradlePath(codeSource(MortarBoundQuery.class)),
            gradlePath(codeSource(MortarGeneratedQuery.class)),
            gradlePath(codeSource(MortarProcessor.class)),
            gradlePath(resourceRoot("META-INF/gradle/incremental.annotation.processors")),
            gradlePath(codeSource(MortarProcessor.class)),
            gradlePath(resourceRoot("META-INF/gradle/incremental.annotation.processors"))
        ), StandardCharsets.UTF_8);

        writeSource(projectDir, "domain/src/main/java/example/r17/TicketPriority.java", """
            package example.r17;

            public enum TicketPriority {
                LOW,
                HIGH
            }
            """);
        writeSource(projectDir, "application/src/main/java/example/r17/TicketReader.java", """
            package example.r17;

            public interface TicketReader {
            }
            """);
        writeInfrastructureSources(projectDir, "summary", "QTicketRecord.TICKET_RECORD.summary.eq(\"Router outage\");");
    }

    private static void writeInfrastructureSources(
        Path projectDir,
        String ticketSummaryField,
        String usageExpression
    ) throws Exception {
        writeSource(projectDir, "infrastructure/src/main/java/example/r17/TicketRecord.java", """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "tickets", alias = "t")
            final class TicketRecord {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "summary", nullable = false)
                String %s;
            }
            """.formatted(ticketSummaryField));
        writeSource(projectDir, "infrastructure/src/main/java/example/r17/TicketStatusRecord.java", """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "ticket_statuses", alias = "ts")
            final class TicketStatusRecord {
                @MortarId
                @MortarColumn(name = "code", nullable = false)
                String code;
            }
            """);
        writeSource(projectDir, "infrastructure/src/main/java/example/r17/TicketUsage.java", """
            package example.r17;

            import dev.mortar.core.QueryRenderer;

            final class TicketUsage {
                void use(QueryRenderer renderer) {
                    %s
                    QTicketRecord.TICKET_RECORD.read(renderer).findById(42L).named("TicketReader.findHeader");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.read(renderer).findAll().named("TicketReader.listStatusOptions");
                }
            }
            """.formatted(usageExpression));
    }

    private static void deleteInfrastructureEntities(Path projectDir) throws Exception {
        Files.delete(projectDir.resolve("infrastructure/src/main/java/example/r17/TicketRecord.java"));
        Files.delete(projectDir.resolve("infrastructure/src/main/java/example/r17/TicketStatusRecord.java"));
        writeSource(projectDir, "infrastructure/src/main/java/example/r17/TicketUsage.java", """
            package example.r17;

            final class TicketUsage {
            }
            """);
    }

    private static void assertDomainAndApplicationAreMortarFree(Path projectDir) throws Exception {
        try (var paths = Files.walk(projectDir)) {
            assertThat(paths
                .filter(path -> path.toString().contains("domain") || path.toString().contains("application"))
                .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith("build.gradle"))
                .map(MortarProcessorGradleIncrementalConvergenceTest::read)
                .filter(content -> content.contains("dev.mortar") || content.contains("mortar-"))
                .toList()).isEmpty();
        }
    }

    private static void runGradle(Path projectDir, String... arguments) {
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(arguments)
            .forwardOutput()
            .build();
    }

    private static String metadata(Path projectDir) throws Exception {
        return read(projectDir.resolve("infrastructure/build/classes/java/main/META-INF/mortar/entities.json"))
            .replace("\r\n", "\n");
    }

    private static String sourceMap(Path projectDir) throws Exception {
        return read(projectDir.resolve("infrastructure/build/classes/java/main/META-INF/mortar/source-map.json"))
            .replace("\r\n", "\n");
    }

    private static Map<String, String> sourceMapInventory(String sourceMap) {
        Matcher matcher = QUERY_FINGERPRINT.matcher(sourceMap);
        Map<String, String> inventory = new TreeMap<>();
        while (matcher.find()) {
            inventory.put(matcher.group(1), matcher.group(2));
        }
        return inventory;
    }

    private static String fingerprint(String sourceMap, String queryId) {
        String fingerprint = sourceMapInventory(sourceMap).get(queryId);
        assertThat(fingerprint).as("fingerprint for " + queryId).isNotNull();
        return fingerprint;
    }

    private static void writeSource(Path projectDir, String relativePath, String source) throws Exception {
        Path sourceFile = projectDir.resolve(relativePath);
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private static Path codeSource(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static Path resourceRoot(String resourceName) throws URISyntaxException {
        URL resource = MortarProcessorGradleIncrementalConvergenceTest.class
            .getClassLoader()
            .getResource(resourceName);
        assertThat(resource).isNotNull();
        return Path.of(resource.toURI()).getParent().getParent().getParent();
    }

    private static String gradlePath(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "/");
    }
}
