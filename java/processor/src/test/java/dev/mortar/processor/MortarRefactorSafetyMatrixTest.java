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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class MortarRefactorSafetyMatrixTest {
    @TempDir
    private Path tempDir;

    @Test
    void r17BaselineGeneratesStableMetadataForRefactorSafetyMatrix() throws Exception {
        CompilationResult result = compile(r17Sources(ticketRecord(), technicianRecord(), ticketStatusRecord(), staleUsage()));

        assertThat(result.compiled()).isTrue();
        String ticketMetamodel = generatedSource(result, "QTicketRecord");
        assertThat(ticketMetamodel)
            .contains("public final ColumnRef<java.lang.String> summary = table.column(\"summary\", \"summary\", java.lang.String.class);")
            .contains("public final RelationRef customer = new RelationRef(")
            .contains("public final RelationRef assignedTechnician = new RelationRef(")
            .contains("public final RelationRef status = new RelationRef(");

        String metadata = metadata(result);
        assertThat(metadata)
            .contains("\"java_type\": \"example.r17.TicketRecord\"")
            .contains("\"property\": \"summary\"")
            .contains("\"property\": \"customer\"")
            .contains("\"property\": \"assignedTechnician\"")
            .contains("\"property\": \"status\"")
            .contains("\"snapshot\": \"example.r17.TicketRecord.findById\"")
            .contains("\"snapshot\": \"example.r17.TicketStatusRecord.findAll\"");
    }

    @Test
    void annotatedFieldRenamesFailAsUnresolvedGeneratedSymbolsUntilConsumersRecover() throws Exception {
        CompilationResult fail = compile(r17Sources(
            ticketRecordWithSummaryRenamed(),
            technicianRecordWithDisplayNameRenamed(),
            ticketStatusRecordWithCodeRenamed(),
            staleUsage()
        ));

        assertThat(fail.compiled()).isFalse();
        assertUnresolvedGeneratedSymbol(fail, "summary");
        assertUnresolvedGeneratedSymbol(fail, "displayName");
        assertUnresolvedGeneratedSymbol(fail, "code");
        assertThat(generatedSource(fail, "QTicketRecord"))
            .contains("public final ColumnRef<java.lang.String> subject = table.column(\"subject\", \"summary\", java.lang.String.class);")
            .doesNotContain("public final ColumnRef<java.lang.String> summary");
        assertThat(generatedSource(fail, "QTechnicianRecord"))
            .contains("public final ColumnRef<java.lang.String> fullName = table.column(\"fullName\", \"display_name\", java.lang.String.class);")
            .doesNotContain("public final ColumnRef<java.lang.String> displayName");
        assertThat(generatedSource(fail, "QTicketStatusRecord"))
            .contains("public final ColumnRef<java.lang.String> statusCode = table.column(\"statusCode\", \"code\", java.lang.String.class);")
            .doesNotContain("public final ColumnRef<java.lang.String> code");

        CompilationResult recover = compile(r17Sources(
            ticketRecordWithSummaryRenamed(),
            technicianRecordWithDisplayNameRenamed(),
            ticketStatusRecordWithCodeRenamed(),
            recoveredRenameUsage()
        ));

        assertThat(recover.compiled())
            .as(recover.errors().toString())
            .isTrue();
        assertThat(generatedSource(recover, "QTicketRecord"))
            .contains("public final ColumnRef<java.lang.String> subject = table.column(\"subject\", \"summary\", java.lang.String.class);")
            .doesNotContain("public final ColumnRef<java.lang.String> summary");
    }

    @Test
    void annotatedFieldDeletionFailsAsUnresolvedGeneratedSymbolUntilConsumerStopsUsingIt() throws Exception {
        CompilationResult fail = compile(r17Sources(
            ticketRecordWithoutSummary(),
            technicianRecord(),
            ticketStatusRecord(),
            staleUsage()
        ));

        assertThat(fail.compiled()).isFalse();
        assertUnresolvedGeneratedSymbol(fail, "summary");
        assertThat(generatedSource(fail, "QTicketRecord"))
            .doesNotContain("public final ColumnRef<java.lang.String> summary");

        CompilationResult recover = compile(r17Sources(
            ticketRecordWithoutSummary(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutDeletedSummary()
        ));

        assertThat(recover.compiled())
            .as(recover.errors().toString())
            .isTrue();
        assertThat(generatedSource(recover, "QTicketRecord"))
            .doesNotContain("public final ColumnRef<java.lang.String> summary");
    }

    @Test
    void relationDeletionFailsAsUnresolvedGeneratedSymbolsUntilConsumersRecover() throws Exception {
        CompilationResult fail = compile(r17Sources(
            ticketRecordWithoutRelations(),
            technicianRecord(),
            ticketStatusRecord(),
            staleUsage()
        ));

        assertThat(fail.compiled()).isFalse();
        assertUnresolvedGeneratedSymbol(fail, "customer");
        assertUnresolvedGeneratedSymbol(fail, "assignedTechnician");
        assertUnresolvedGeneratedSymbol(fail, "status");
        assertThat(generatedSource(fail, "QTicketRecord"))
            .doesNotContain("public final RelationRef customer")
            .doesNotContain("public final RelationRef assignedTechnician")
            .doesNotContain("public final RelationRef status");

        CompilationResult recover = compile(r17Sources(
            ticketRecordWithoutRelations(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutDeletedRelations()
        ));

        assertThat(recover.compiled())
            .as(recover.errors().toString())
            .isTrue();
        assertThat(generatedSource(recover, "QTicketRecord"))
            .doesNotContain("public final RelationRef customer")
            .doesNotContain("public final RelationRef assignedTechnician")
            .doesNotContain("public final RelationRef status");
    }

    @Test
    void relationMetadataValidationFailuresUseProcessorDiagnosticCategory() throws Exception {
        CompilationResult baseline = compile(r17Sources(
            ticketRecord(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutRelationMetadata()
        ));
        CompilationResult result = compile(r17Sources(
            ticketRecordWithBlankRelationTargetColumn(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutRelationMetadata()
        ));
        CompilationResult recover = compile(r17Sources(
            ticketRecord(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutRelationMetadata()
        ));

        assertThat(baseline.compiled()).isTrue();
        assertThat(result.compiled()).isFalse();
        assertThat(result.errors()).anyMatch(message -> message.contains("MORTAR_PROCESSOR_007"));
        assertThat(recover.compiled()).isTrue();
    }

    @Test
    void columnAndRelationMetadataChangesProduceDifferentGeneratedSqlInputs() throws Exception {
        CompilationResult baseline = compile(r17Sources(ticketRecord(), technicianRecord(), ticketStatusRecord(), usageWithoutRelationMetadata()));
        CompilationResult changed = compile(r17Sources(
            ticketRecordWithChangedColumnAndRelationMetadata(),
            technicianRecord(),
            ticketStatusRecord(),
            usageWithoutRelationMetadata()
        ));

        assertThat(baseline.compiled()).isTrue();
        assertThat(changed.compiled()).isTrue();

        String baselineMetamodel = generatedSource(baseline, "QTicketRecord");
        String changedMetamodel = generatedSource(changed, "QTicketRecord");
        assertThat(generatedSource(baseline, "QTicketRecord"))
            .contains("summary = table.column(\"summary\", \"summary\", java.lang.String.class)")
            .contains("table.column(\"status\", \"status_code\", java.lang.Object.class)");
        assertThat(changedMetamodel)
            .doesNotContain("summary = table.column(\"summary\", \"summary\", java.lang.String.class)")
            .doesNotContain("table.column(\"status\", \"status_code\", java.lang.Object.class)");
        assertThat(changedMetamodel)
            .contains("summary = table.column(\"summary\", \"ticket_summary\", java.lang.String.class)")
            .contains("table.column(\"status\", \"workflow_status_code\", java.lang.Object.class)");
        assertThat(baselineMetamodel).isNotEqualTo(changedMetamodel);

        assertThat(metadata(changed))
            .contains("\"column\": \"ticket_summary\"")
            .contains("\"local_column\": \"workflow_status_code\"");
    }

    private CompilationResult compile(Map<String, String> sources) throws Exception {
        String id = String.valueOf(System.nanoTime());
        Path sourceDir = tempDir.resolve("source-" + id);
        Path classDir = tempDir.resolve("classes-" + id);
        Path generatedDir = tempDir.resolve("generated-" + id);
        Files.createDirectories(sourceDir);
        Files.createDirectories(classDir);
        Files.createDirectories(generatedDir);

        List<Path> sourceFiles = sources.entrySet().stream()
            .map(entry -> writeSource(sourceDir, entry.getKey(), entry.getValue()))
            .toList();

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
                fileManager.getJavaFileObjectsFromPaths(sourceFiles)
            ).call();

            List<DiagnosticInfo> diagnosticInfos = diagnostics.getDiagnostics().stream()
                .map(MortarRefactorSafetyMatrixTest::diagnosticInfo)
                .toList();
            return new CompilationResult(compiled, diagnosticInfos, generatedDir, classDir);
        }
    }

    private static DiagnosticInfo diagnosticInfo(Diagnostic<? extends JavaFileObject> diagnostic) {
        JavaFileObject source = diagnostic.getSource();
        String sourceName = source == null ? "" : source.getName().replace("\\", "/");
        return new DiagnosticInfo(
            diagnostic.getKind(),
            diagnostic.getMessage(Locale.ROOT),
            sourceName
        );
    }

    private Path writeSource(Path sourceDir, String relativePath, String source) {
        try {
            Path sourceFile = sourceDir.resolve(relativePath);
            Files.createDirectories(sourceFile.getParent());
            Files.writeString(sourceFile, source, StandardCharsets.UTF_8);
            return sourceFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write " + relativePath, exception);
        }
    }

    private static Map<String, String> r17Sources(
        String ticketSource,
        String technicianSource,
        String statusSource,
        String usageSource
    ) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("example/r17/CustomerRecord.java", customerRecord());
        sources.put("example/r17/TechnicianRecord.java", technicianSource);
        sources.put("example/r17/TicketStatusRecord.java", statusSource);
        sources.put("example/r17/TicketRecord.java", ticketSource);
        sources.put("example/r17/TicketUsage.java", usageSource);
        return sources;
    }

    private static String generatedSource(CompilationResult result, String generatedType) throws Exception {
        return Files.readString(
            result.generatedDir().resolve("example").resolve("r17").resolve(generatedType + ".java"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }

    private static String metadata(CompilationResult result) throws Exception {
        return Files.readString(
            result.classDir().resolve("META-INF").resolve("mortar").resolve("entities.json"),
            StandardCharsets.UTF_8
        ).replace("\r\n", "\n");
    }

    private static void assertUnresolvedGeneratedSymbol(CompilationResult result, String symbol) {
        assertThat(result.diagnostics())
            .as("javac should report an unresolved generated symbol for " + symbol)
            .anySatisfy(diagnostic -> {
                assertThat(diagnostic.kind()).isEqualTo(Diagnostic.Kind.ERROR);
                assertThat(diagnostic.sourceName()).endsWith("example/r17/TicketUsage.java");
                assertThat(diagnostic.message()).contains(symbol);
            });
    }

    private static String customerRecord() {
        return """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "customers", alias = "cu")
            final class CustomerRecord {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "name", nullable = false)
                String name;
            }
            """;
    }

    private static String technicianRecord() {
        return """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "technicians", alias = "te")
            final class TechnicianRecord {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

                @MortarColumn(name = "display_name", nullable = false)
                String displayName;

                @MortarColumn(name = "region", nullable = false)
                String region;
            }
            """;
    }

    private static String technicianRecordWithDisplayNameRenamed() {
        return technicianRecord().replace("String displayName;", "String fullName;");
    }

    private static String ticketStatusRecord() {
        return """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;

            @MortarEntity(table = "ticket_statuses", alias = "ts")
            final class TicketStatusRecord {
                @MortarId
                @MortarColumn(name = "code", nullable = false)
                String code;

                @MortarColumn(name = "name", nullable = false)
                String name;
            }
            """;
    }

    private static String ticketStatusRecordWithCodeRenamed() {
        return ticketStatusRecord().replace("String code;", "String statusCode;");
    }

    private static String ticketRecord() {
        return ticketRecordWithBody("""
                @MortarColumn(name = "summary", nullable = false)
                String summary;
            """, relations("status_code"));
    }

    private static String ticketRecordWithSummaryRenamed() {
        return ticketRecordWithBody("""
                @MortarColumn(name = "summary", nullable = false)
                String subject;
            """, relations("status_code"));
    }

    private static String ticketRecordWithoutSummary() {
        return ticketRecordWithBody("", relations("status_code"));
    }

    private static String ticketRecordWithoutRelations() {
        return ticketRecordWithBody("""
                @MortarColumn(name = "summary", nullable = false)
                String summary;
            """, "");
    }

    private static String ticketRecordWithBlankRelationTargetColumn() {
        return ticketRecordWithBody("""
                @MortarColumn(name = "summary", nullable = false)
                String summary;
            """, """
                @MortarRelation(target = TicketStatusRecord.class, localColumn = "status_code", targetColumn = " ")
                TicketStatusRecord status;
            """);
    }

    private static String ticketRecordWithChangedColumnAndRelationMetadata() {
        return ticketRecordWithBody("""
                @MortarColumn(name = "ticket_summary", nullable = false)
                String summary;
            """, relations("workflow_status_code"));
    }

    private static String ticketRecordWithBody(String summaryField, String relationFields) {
        return """
            package example.r17;

            import dev.mortar.processor.MortarColumn;
            import dev.mortar.processor.MortarEntity;
            import dev.mortar.processor.MortarId;
            import dev.mortar.processor.MortarRelation;
            import java.time.LocalDate;

            @MortarEntity(table = "tickets", alias = "t")
            final class TicketRecord {
                @MortarId
                @MortarColumn(name = "id", nullable = false)
                Long id;

            %s
                @MortarColumn(name = "priority", nullable = false)
                String priority;

                @MortarColumn(name = "opened_on", nullable = false)
                LocalDate openedOn;

            %s
            }
            """.formatted(summaryField, relationFields);
    }

    private static String relations(String statusLocalColumn) {
        return """
                @MortarRelation(target = CustomerRecord.class, localColumn = "customer_id", nullable = false)
                CustomerRecord customer;

                @MortarRelation(target = TechnicianRecord.class, localColumn = "assigned_technician_id")
                TechnicianRecord assignedTechnician;

                @MortarRelation(target = TicketStatusRecord.class, localColumn = "%s", targetColumn = "code", nullable = false)
                TicketStatusRecord status;
            """.formatted(statusLocalColumn);
    }

    private static String staleUsage() {
        return """
            package example.r17;

            import dev.mortar.core.QueryRenderer;

            final class TicketUsage {
                void use(QueryRenderer renderer) {
                    QTicketRecord.TICKET_RECORD.summary.eq("Router outage");
                    QTechnicianRecord.TECHNICIAN_RECORD.displayName.eq("Grace Hopper");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.code.eq("open");
                    QTicketRecord.TICKET_RECORD.customer.innerJoin();
                    QTicketRecord.TICKET_RECORD.assignedTechnician.leftJoin();
                    QTicketRecord.TICKET_RECORD.status.innerJoin();
                    QTicketRecord.TICKET_RECORD.read(renderer).findById(42L).named("TicketReader.findHeader");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.read(renderer).findAll().named("TicketReader.listStatusOptions");
                }
            }
            """;
    }

    private static String recoveredRenameUsage() {
        return staleUsage()
            .replace("summary.eq", "subject.eq")
            .replace("displayName.eq", "fullName.eq")
            .replace("code.eq", "statusCode.eq");
    }

    private static String usageWithoutDeletedSummary() {
        return """
            package example.r17;

            import dev.mortar.core.QueryRenderer;

            final class TicketUsage {
                void use(QueryRenderer renderer) {
                    QTechnicianRecord.TECHNICIAN_RECORD.displayName.eq("Grace Hopper");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.code.eq("open");
                    QTicketRecord.TICKET_RECORD.customer.innerJoin();
                    QTicketRecord.TICKET_RECORD.assignedTechnician.leftJoin();
                    QTicketRecord.TICKET_RECORD.status.innerJoin();
                    QTicketRecord.TICKET_RECORD.read(renderer).findById(42L).named("TicketReader.findHeader");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.read(renderer).findAll().named("TicketReader.listStatusOptions");
                }
            }
            """;
    }

    private static String usageWithoutDeletedRelations() {
        return """
            package example.r17;

            import dev.mortar.core.QueryRenderer;

            final class TicketUsage {
                void use(QueryRenderer renderer) {
                    QTicketRecord.TICKET_RECORD.summary.eq("Router outage");
                    QTechnicianRecord.TECHNICIAN_RECORD.displayName.eq("Grace Hopper");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.code.eq("open");
                    QTicketRecord.TICKET_RECORD.read(renderer).findById(42L).named("TicketReader.findHeader");
                    QTicketStatusRecord.TICKET_STATUS_RECORD.read(renderer).findAll().named("TicketReader.listStatusOptions");
                }
            }
            """;
    }

    private static String usageWithoutRelationMetadata() {
        return usageWithoutDeletedRelations();
    }

    private record CompilationResult(boolean compiled, List<DiagnosticInfo> diagnostics, Path generatedDir, Path classDir) {
        private List<String> errors() {
            return diagnostics.stream()
                .filter(diagnostic -> diagnostic.kind() == Diagnostic.Kind.ERROR)
                .map(DiagnosticInfo::message)
                .toList();
        }
    }

    private record DiagnosticInfo(
        Diagnostic.Kind kind,
        String message,
        String sourceName
    ) {
    }
}
