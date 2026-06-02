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
    }
}
