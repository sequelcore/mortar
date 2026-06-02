package dev.mortar.processor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class MortarProcessorMetadataFileTest {
    @TempDir
    private Path tempDir;

    @Test
    void emitsMortarMetadataFileForGeneratedEntities() throws Exception {
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

        compile(classDir, generatedDir, clientSource);

        Path metadataFile = classDir.resolve("META-INF").resolve("mortar").resolve("entities.json");
        assertThat(Files.readString(metadataFile, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .isEqualTo("""
                {
                  "format": "mortar-metadata-v1",
                  "entities": [
                    {
                      "java_type": "example.Client",
                      "table": "clients",
                      "alias": "c",
                      "columns": [
                        {
                          "property": "id",
                          "column": "id",
                          "java_type": "java.lang.Long"
                        },
                        {
                          "property": "name",
                          "column": "name",
                          "java_type": "java.lang.String"
                        }
                      ],
                      "relations": [],
                      "queries": [
                        {
                          "id": "example.Client.findAll",
                          "name": "findAll",
                          "shape": "findAll",
                          "generated_source": {
                            "java_type": "example.QClient",
                            "member": "read.findAll",
                            "generated_type": "example.QClient.Read"
                          },
                          "parameters": [],
                          "row_type": "example.QClient.FindAllRow",
                          "snapshot": "example.Client.findAll"
                        },
                        {
                          "id": "example.Client.findById",
                          "name": "findById",
                          "shape": "findById",
                          "generated_source": {
                            "java_type": "example.QClient",
                            "member": "read.findById",
                            "generated_type": "example.QClient.Read"
                          },
                          "parameters": [
                            {
                              "name": "id",
                              "java_type": "java.lang.Long"
                            }
                          ],
                          "row_type": "example.QClient.FindByIdRow",
                          "snapshot": "example.Client.findById"
                        }
                      ]
                    }
                  ]
                }
                """);

        Path sourceMapFile = classDir.resolve("META-INF").resolve("mortar").resolve("source-map.json");
        String sourceMap = Files.readString(sourceMapFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertThat(sourceMap)
            .contains("\"format\": \"mortar-source-map-v1\"")
            .contains("\"path\": \"META-INF/mortar/entities.json\"")
            .contains("\"id\": \"example.Client.findAll\"")
            .contains("\"id\": \"example.Client.findById\"")
            .contains("\"generated_entity_type\": \"example.QClient\"")
            .contains("\"generated_read_namespace\": \"example.QClient.Read\"")
            .contains("\"generated_member\": \"read.findById\"")
            .contains("\"query_name\": \"findById\"")
            .contains("\"row_type\": \"example.QClient.FindByIdRow\"")
            .contains("\"kind\": \"java-type\"")
            .contains("\"java_type\": \"example.Client\"")
            .contains("\"fingerprint\": \"sha256:");
    }

    @Test
    void overwritesSharedMetadataAndSourceMapWhenNoEntitiesRemain() throws Exception {
        Path sourceDir = tempDir.resolve("stale-source");
        Path classDir = tempDir.resolve("stale-classes");
        Path generatedDir = tempDir.resolve("stale-generated");
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
            }
            """, StandardCharsets.UTF_8);

        compile(classDir, generatedDir, clientSource);
        Path metadataFile = classDir.resolve("META-INF").resolve("mortar").resolve("entities.json");
        Path sourceMapFile = classDir.resolve("META-INF").resolve("mortar").resolve("source-map.json");
        assertThat(Files.readString(metadataFile, StandardCharsets.UTF_8)).contains("example.Client.findById");
        assertThat(Files.readString(sourceMapFile, StandardCharsets.UTF_8)).contains("example.Client.findById");

        Path plainSource = sourceDir.resolve("example").resolve("Plain.java");
        Files.writeString(plainSource, """
            package example;

            final class Plain {
            }
            """, StandardCharsets.UTF_8);

        compile(classDir, generatedDir, plainSource);

        assertThat(Files.readString(metadataFile, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("\"entities\": []")
            .doesNotContain("example.Client.findById");
        assertThat(Files.readString(sourceMapFile, StandardCharsets.UTF_8).replace("\r\n", "\n"))
            .contains("\"queries\": []")
            .doesNotContain("example.Client.findById");
    }

    @Test
    void includesMortarEntitiesGeneratedInLaterProcessingRounds() throws Exception {
        Path sourceDir = tempDir.resolve("round-source");
        Path classDir = tempDir.resolve("round-classes");
        Path generatedDir = tempDir.resolve("round-generated");
        Files.createDirectories(sourceDir.resolve("example"));
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        Path triggerSource = sourceDir.resolve("example").resolve("Trigger.java");
        Files.writeString(triggerSource, """
            package example;

            @Deprecated
            final class Trigger {
            }
            """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            fileManager.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classDir));
            fileManager.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generatedDir));

            JavaCompiler.CompilationTask task = compiler.getTask(
                null,
                fileManager,
                null,
                List.of("-classpath", System.getProperty("java.class.path")),
                null,
                fileManager.getJavaFileObjectsFromPaths(List.of(triggerSource))
            );
            task.setProcessors(List.of(new LaterRoundEntityProcessor(), new MortarProcessor()));
            boolean compiled = task.call();

            assertThat(compiled).isTrue();
        }

        assertThat(Files.readString(
            generatedDir.resolve("example").resolve("QGeneratedClient.java"),
            StandardCharsets.UTF_8
        )).contains("public final class QGeneratedClient");
        assertThat(Files.readString(
            classDir.resolve("META-INF").resolve("mortar").resolve("entities.json"),
            StandardCharsets.UTF_8
        )).contains("\"java_type\": \"example.GeneratedClient\"");
        assertThat(Files.readString(
            classDir.resolve("META-INF").resolve("mortar").resolve("source-map.json"),
            StandardCharsets.UTF_8
        )).contains("\"id\": \"example.GeneratedClient.findById\"");
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

    @SupportedAnnotationTypes("java.lang.Deprecated")
    @SupportedSourceVersion(SourceVersion.RELEASE_21)
    private static final class LaterRoundEntityProcessor extends AbstractProcessor {
        private boolean generated;

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (generated || roundEnv.processingOver()) {
                return false;
            }
            try {
                JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile("example.GeneratedClient");
                try (var writer = sourceFile.openWriter()) {
                    writer.write("""
                        package example;

                        import dev.mortar.processor.MortarColumn;
                        import dev.mortar.processor.MortarEntity;
                        import dev.mortar.processor.MortarId;

                        @MortarEntity(table = "generated_clients", alias = "gc")
                        final class GeneratedClient {
                            @MortarId
                            @MortarColumn(name = "id", nullable = false)
                            Long id;
                        }
                        """);
                }
                generated = true;
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to generate later-round entity", exception);
            }
            return false;
        }
    }
}
