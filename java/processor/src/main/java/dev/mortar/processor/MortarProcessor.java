package dev.mortar.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@SupportedAnnotationTypes({
    "dev.mortar.processor.MortarColumn",
    "dev.mortar.processor.MortarEntity",
    "dev.mortar.processor.MortarId",
    "dev.mortar.processor.MortarRelation",
    "jakarta.persistence.Column",
    "jakarta.persistence.Entity",
    "jakarta.persistence.Id",
    "jakarta.persistence.JoinColumn",
    "jakarta.persistence.Table"
})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class MortarProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        if (roundEnvironment == null || annotations.isEmpty() || roundEnvironment.processingOver()) {
            return false;
        }

        List<EntityMetadata> metadata = new ArrayList<>();
        for (Element element : roundEnvironment.getRootElements()) {
            if (element instanceof TypeElement typeElement) {
                if (isMortarEntity(typeElement) || hasAnnotation(typeElement, "jakarta.persistence.Entity")) {
                    generateMetamodel(typeElement).ifPresent(metadata::add);
                }
            }
        }

        if (!metadata.isEmpty()) {
            writeMetadataFile(metadata);
        }

        return true;
    }

    private Optional<EntityMetadata> generateMetamodel(TypeElement entity) {
        String packageName = processingEnv.getElementUtils().getPackageOf(entity).getQualifiedName().toString();
        String entityName = entity.getSimpleName().toString();
        String generatedClassName = "Q" + entityName;
        String tableName = tableName(entity);
        String alias = alias(entity, entityName);
        if (!validate(entity)) {
            return Optional.empty();
        }
        List<ColumnModel> columns = columns(entity);
        List<RelationModel> relations = relations(entity);

        try {
            JavaFileObject sourceFile = processingEnv.getFiler()
                .createSourceFile(packageName + "." + generatedClassName, entity);
            try (Writer writer = sourceFile.openWriter()) {
                writer.write(renderSource(packageName, generatedClassName, entityName, tableName, alias, columns, relations));
            }
            return Optional.of(new EntityMetadata(
                packageName + "." + entityName,
                tableName,
                alias,
                columns,
                relations
            ));
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate Mortar metamodel for " + entityName + ": " + exception.getMessage(),
                entity
            );
            return Optional.empty();
        }
    }

    private void writeMetadataFile(List<EntityMetadata> metadata) {
        try {
            FileObject metadataFile = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/mortar/entities.json");
            try (Writer writer = metadataFile.openWriter()) {
                writer.write(renderMetadata(metadata));
            }
        } catch (IOException exception) {
            processingEnv.getMessager().printMessage(
                Diagnostic.Kind.ERROR,
                "Failed to generate Mortar metadata: " + exception.getMessage()
            );
        }
    }

    private boolean validate(TypeElement entity) {
        boolean valid = true;
        boolean hasId = false;
        Set<String> columnNames = new HashSet<>();

        for (VariableElement field : ElementFilter.fieldsIn(entity.getEnclosedElements())) {
            MortarColumn column = field.getAnnotation(MortarColumn.class);
            MortarRelation relation = field.getAnnotation(MortarRelation.class);
            Optional<String> columnName = columnName(field);
            Optional<String> relationColumn = relationLocalColumn(field);

            if (isId(field)) {
                hasId = true;
            }

            if (columnName.isPresent()) {
                String name = columnName.get();
                if (!columnNames.add(name)) {
                    error("MORTAR_PROCESSOR_002 duplicate column '" + name + "'", field);
                    valid = false;
                }
                if (hasUnsupportedType(field)) {
                    error("MORTAR_PROCESSOR_003 unsupported column type '" + field.asType() + "'", field);
                    valid = false;
                }
            }

            if (relation != null || relationColumn.isPresent()) {
                String localColumn = relationColumn.orElse("");
                if (localColumn.isBlank()) {
                    error("MORTAR_PROCESSOR_004 relation localColumn cannot be blank", field);
                    valid = false;
                }
                if (!localColumn.isBlank() && !columnNames.add(localColumn)) {
                    error("MORTAR_PROCESSOR_002 duplicate column '" + localColumn + "'", field);
                    valid = false;
                }
            }
        }

        if (!hasId) {
            error("MORTAR_PROCESSOR_001 entity must declare a @MortarId field", entity);
            valid = false;
        }

        return valid;
    }

    private boolean hasUnsupportedType(VariableElement field) {
        if (field.asType().getKind() != TypeKind.DECLARED) {
            return false;
        }

        DeclaredType declaredType = (DeclaredType) field.asType();
        return !declaredType.getTypeArguments().isEmpty();
    }

    private void error(String message, Element element) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
    }

    private List<ColumnModel> columns(TypeElement entity) {
        List<ColumnModel> columns = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(entity.getEnclosedElements())) {
            Optional<String> columnName = columnName(field);
            if (columnName.isPresent()) {
                columns.add(new ColumnModel(
                    field.getSimpleName().toString(),
                    columnName.get(),
                    field.asType().toString(),
                    isId(field)
                ));
            }
        }
        return List.copyOf(columns);
    }

    private List<RelationModel> relations(TypeElement entity) {
        List<RelationModel> relations = new ArrayList<>();
        for (VariableElement field : ElementFilter.fieldsIn(entity.getEnclosedElements())) {
            Optional<String> localColumn = relationLocalColumn(field);
            if (localColumn.isPresent()) {
                TypeElement target = relationTarget(field);
                String targetTableName = tableName(target);
                String targetAlias = alias(target, target.getSimpleName().toString());
                String targetColumn = relationTargetColumn(field).orElse("id");
                relations.add(new RelationModel(
                    field.getSimpleName().toString(),
                    localColumn.get(),
                    targetTableName,
                    targetAlias,
                    targetColumn,
                    relationNullable(field)
                ));
            }
        }
        return List.copyOf(relations);
    }

    private String renderSource(
        String packageName,
        String generatedClassName,
        String entityName,
        String tableName,
        String alias,
        List<ColumnModel> columns,
        List<RelationModel> relations
    ) {
        StringBuilder source = new StringBuilder(512);
        source.append("package ").append(packageName).append(";\n\n");
        source.append("import dev.mortar.core.ColumnRef;\n");
        source.append("import dev.mortar.core.MortarTable;\n");
        if (!relations.isEmpty()) {
            source.append("import dev.mortar.core.RelationRef;\n");
        }
        source.append("import dev.mortar.jdbc.MortarGeneratedQuery;\n");
        source.append("import dev.mortar.core.TableRef;\n\n");
        source.append("/**\n");
        source.append(" * Mortar metamodel for SQL table {@code ").append(tableName)
            .append("} using alias {@code ").append(alias).append("}.\n");
        source.append(" */\n");
        source.append("public final class ").append(generatedClassName).append(" implements MortarTable {\n");
        source.append("    public static final ").append(generatedClassName).append(" ")
            .append(constantName(entityName)).append(" = new ").append(generatedClassName).append("();\n\n");
        source.append("    /**\n");
        source.append("     * SQL table reference for {@code ").append(tableName)
            .append("} as {@code ").append(alias).append("}.\n");
        source.append("     */\n");
        source.append("    public final TableRef table = new TableRef(\"").append(tableName).append("\", \"")
            .append(alias).append("\");\n");
        for (ColumnModel column : columns) {
            source.append("    /**\n");
            source.append("     * SQL column {@code ").append(tableName).append(".").append(column.columnName())
                .append("}; Java property {@code ").append(column.propertyName()).append("}.\n");
            source.append("     */\n");
            source.append("    public final ColumnRef<").append(column.javaType()).append("> ")
                .append(column.propertyName())
                .append(" = table.column(\"").append(column.propertyName()).append("\", \"")
                .append(column.columnName()).append("\", ").append(column.javaType()).append(".class);\n");
        }
        for (RelationModel relation : relations) {
            source.append("    /**\n");
            source.append("     * Relationship path {@code ").append(relation.propertyName()).append("} using local column {@code ")
                .append(tableName).append(".").append(relation.localColumn()).append("}.\n");
            source.append("     */\n");
            source.append("    public final RelationRef ").append(relation.propertyName()).append(" = new RelationRef(\n");
            source.append("        \"").append(relation.propertyName()).append("\",\n");
            source.append("        new TableRef(\"").append(relation.targetTable()).append("\", \"")
                .append(relation.targetAlias()).append("\"),\n");
            source.append("        table.column(\"").append(relation.propertyName()).append("\", \"")
                .append(relation.localColumn()).append("\", java.lang.Object.class),\n");
            source.append("        new TableRef(\"").append(relation.targetTable()).append("\", \"")
                .append(relation.targetAlias()).append("\").column(\"").append(relation.targetColumn())
                .append("\", \"").append(relation.targetColumn()).append("\", java.lang.Object.class),\n");
            source.append("        ").append(relation.nullable()).append("\n");
            source.append("    );\n");
        }
        appendFindAllExecutor(source, generatedClassName, columns);
        appendFindByIdExecutor(source, generatedClassName, columns);
        source.append("\n");
        source.append("    @Override\n");
        source.append("    public TableRef table() {\n");
        source.append("        return table;\n");
        source.append("    }\n\n");
        source.append("    private ").append(generatedClassName).append("() {\n");
        source.append("    }\n");
        source.append("}\n");
        return source.toString();
    }

    private void appendFindAllExecutor(StringBuilder source, String generatedClassName, List<ColumnModel> columns) {
        source.append("\n");
        source.append("    public FindAllQuery findAll(dev.mortar.core.QueryRenderer renderer) {\n");
        source.append("        return new FindAllQuery(renderer);\n");
        source.append("    }\n\n");
        source.append("    public record FindAllParameters() {\n");
        source.append("    }\n\n");
        appendRowRecord(source, "FindAllRow", columns);
        source.append("    public static final class FindAllQuery implements MortarGeneratedQuery<FindAllParameters, FindAllRow> {\n");
        source.append("        private final dev.mortar.core.RenderedQuery renderedQuery;\n\n");
        source.append("        public FindAllQuery(dev.mortar.core.QueryRenderer renderer) {\n");
        source.append("            this.renderedQuery = java.util.Objects.requireNonNull(renderer, \"renderer cannot be null\").render(findAllSpec());\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public String sql() {\n");
        source.append("            return renderedQuery.sql();\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public java.util.List<java.lang.Class<?>> parameterTypes() {\n");
        source.append("            return java.util.List.of();\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public dev.mortar.core.QueryMetadata metadata() {\n");
        source.append("            return renderedQuery.metadata();\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public void bind(java.sql.PreparedStatement statement, FindAllParameters parameters) throws java.sql.SQLException {\n");
        source.append("            java.util.Objects.requireNonNull(statement, \"statement cannot be null\");\n");
        source.append("            java.util.Objects.requireNonNull(parameters, \"parameters cannot be null\");\n");
        source.append("        }\n\n");
        appendMapMethod(source, "FindAllRow", columns);
        source.append("    }\n\n");
        source.append("    private static dev.mortar.core.QuerySpec findAllSpec() {\n");
        source.append("        ").append(generatedClassName).append(" table = ").append(generatedClassName).append(".")
            .append(constantName(generatedClassName.substring(1))).append(";\n");
        source.append("        return new dev.mortar.core.SimpleMortarDb()\n");
        source.append("            .from(table)\n");
        appendSelectColumns(source, columns);
        source.append("            .build();\n");
        source.append("    }\n");
    }

    private void appendFindByIdExecutor(StringBuilder source, String generatedClassName, List<ColumnModel> columns) {
        Optional<ColumnModel> idColumn = columns.stream()
            .filter(ColumnModel::id)
            .findFirst();
        if (idColumn.isEmpty()) {
            return;
        }

        ColumnModel id = idColumn.get();
        source.append("\n");
        source.append("    public FindByIdQuery findById(dev.mortar.core.QueryRenderer renderer) {\n");
        source.append("        return new FindByIdQuery(renderer);\n");
        source.append("    }\n\n");
        source.append("    public record FindByIdParameters(").append(id.javaType()).append(" ")
            .append(id.propertyName()).append(") {\n");
        source.append("    }\n\n");
        appendRowRecord(source, "FindByIdRow", columns);
        source.append("    public static final class FindByIdQuery implements MortarGeneratedQuery<FindByIdParameters, FindByIdRow> {\n");
        source.append("        private final dev.mortar.core.RenderedQuery renderedQuery;\n\n");
        source.append("        public FindByIdQuery(dev.mortar.core.QueryRenderer renderer) {\n");
        source.append("            this.renderedQuery = java.util.Objects.requireNonNull(renderer, \"renderer cannot be null\").render(findByIdSpec());\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public String sql() {\n");
        source.append("            return renderedQuery.sql();\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public java.util.List<java.lang.Class<?>> parameterTypes() {\n");
        source.append("            return java.util.List.of(").append(classLiteral(id.javaType())).append(");\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public dev.mortar.core.QueryMetadata metadata() {\n");
        source.append("            return renderedQuery.metadata();\n");
        source.append("        }\n\n");
        source.append("        @Override\n");
        source.append("        public void bind(java.sql.PreparedStatement statement, FindByIdParameters parameters) throws java.sql.SQLException {\n");
        appendBindStatement(source, id);
        source.append("        }\n\n");
        appendMapMethod(source, "FindByIdRow", columns);
        source.append("    }\n\n");
        source.append("    private static dev.mortar.core.QuerySpec findByIdSpec() {\n");
        source.append("        ").append(generatedClassName).append(" table = ").append(generatedClassName).append(".")
            .append(constantName(generatedClassName.substring(1))).append(";\n");
        source.append("        return new dev.mortar.core.SimpleMortarDb()\n");
        source.append("            .from(table)\n");
        appendSelectColumns(source, columns);
        source.append("            .where(table.").append(id.propertyName()).append(".eq(")
            .append(dummyValue(id.javaType())).append("))\n");
        source.append("            .build();\n");
        source.append("    }\n");
        appendReadHelpers(source, columns);
    }

    private void appendRowRecord(StringBuilder source, String rowType, List<ColumnModel> columns) {
        source.append("    public record ").append(rowType).append("(");
        for (int index = 0; index < columns.size(); index++) {
            ColumnModel column = columns.get(index);
            if (index > 0) {
                source.append(", ");
            }
            source.append(column.javaType()).append(" ").append(column.propertyName());
        }
        source.append(") {\n");
        source.append("    }\n\n");
    }

    private void appendMapMethod(StringBuilder source, String rowType, List<ColumnModel> columns) {
        source.append("        @Override\n");
        source.append("        public ").append(rowType).append(" map(java.sql.ResultSet resultSet) throws java.sql.SQLException {\n");
        source.append("            return new ").append(rowType).append("(");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                source.append(", ");
            }
            source.append(readExpression(columns.get(index), index + 1));
        }
        source.append(");\n");
        source.append("        }\n");
    }

    private void appendSelectColumns(StringBuilder source, List<ColumnModel> columns) {
        source.append("            .select(");
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                source.append(", ");
            }
            source.append("table.").append(columns.get(index).propertyName());
        }
        source.append(")\n");
    }

    private void appendBindStatement(StringBuilder source, ColumnModel id) {
        String property = id.propertyName();
        String type = id.javaType();
        if (!isPrimitive(type)) {
            source.append("            if (parameters.").append(property).append("() == null) {\n");
            source.append("                statement.setNull(1, ").append(sqlType(type)).append(");\n");
            source.append("                return;\n");
            source.append("            }\n");
        }
        source.append("            ").append(setterExpression(type, "parameters." + property + "()")).append("\n");
    }

    private String setterExpression(String javaType, String valueExpression) {
        return switch (javaType) {
            case "java.lang.String" -> "statement.setString(1, " + valueExpression + ");";
            case "java.lang.Long", "long" -> "statement.setLong(1, " + valueExpression + ");";
            case "java.lang.Integer", "int" -> "statement.setInt(1, " + valueExpression + ");";
            case "java.lang.Boolean", "boolean" -> "statement.setBoolean(1, " + valueExpression + ");";
            case "java.math.BigDecimal" -> "statement.setBigDecimal(1, " + valueExpression + ");";
            case "java.time.LocalDate" -> "statement.setDate(1, java.sql.Date.valueOf(" + valueExpression + "));";
            case "java.time.LocalDateTime" -> "statement.setTimestamp(1, java.sql.Timestamp.valueOf(" + valueExpression + "));";
            case "java.time.Instant" -> "statement.setTimestamp(1, java.sql.Timestamp.from(" + valueExpression + "));";
            default -> "statement.setObject(1, " + valueExpression + ");";
        };
    }

    private String readExpression(ColumnModel column, int index) {
        return switch (column.javaType()) {
            case "java.lang.String" -> "resultSet.getString(" + index + ")";
            case "java.lang.Long" -> "readLong(resultSet, " + index + ")";
            case "long" -> "resultSet.getLong(" + index + ")";
            case "java.lang.Integer" -> "readInteger(resultSet, " + index + ")";
            case "int" -> "resultSet.getInt(" + index + ")";
            case "java.lang.Boolean" -> "readBoolean(resultSet, " + index + ")";
            case "boolean" -> "resultSet.getBoolean(" + index + ")";
            case "java.math.BigDecimal" -> "resultSet.getBigDecimal(" + index + ")";
            case "java.time.LocalDate" -> "readLocalDate(resultSet, " + index + ")";
            case "java.time.LocalDateTime" -> "readLocalDateTime(resultSet, " + index + ")";
            case "java.time.Instant" -> "readInstant(resultSet, " + index + ")";
            default -> "resultSet.getObject(" + index + ", " + classLiteral(column.javaType()) + ")";
        };
    }

    private void appendReadHelpers(StringBuilder source, List<ColumnModel> columns) {
        Set<String> types = new HashSet<>();
        columns.forEach(column -> types.add(column.javaType()));
        if (types.contains("java.lang.Long")) {
            source.append("\n");
            source.append("    private static java.lang.Long readLong(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        long value = resultSet.getLong(index);\n");
            source.append("        return resultSet.wasNull() ? null : value;\n");
            source.append("    }\n");
        }
        if (types.contains("java.lang.Integer")) {
            source.append("\n");
            source.append("    private static java.lang.Integer readInteger(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        int value = resultSet.getInt(index);\n");
            source.append("        return resultSet.wasNull() ? null : value;\n");
            source.append("    }\n");
        }
        if (types.contains("java.lang.Boolean")) {
            source.append("\n");
            source.append("    private static java.lang.Boolean readBoolean(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        boolean value = resultSet.getBoolean(index);\n");
            source.append("        return resultSet.wasNull() ? null : value;\n");
            source.append("    }\n");
        }
        if (types.contains("java.time.LocalDate")) {
            source.append("\n");
            source.append("    private static java.time.LocalDate readLocalDate(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        java.sql.Date value = resultSet.getDate(index);\n");
            source.append("        return value == null ? null : value.toLocalDate();\n");
            source.append("    }\n");
        }
        if (types.contains("java.time.LocalDateTime")) {
            source.append("\n");
            source.append("    private static java.time.LocalDateTime readLocalDateTime(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        java.sql.Timestamp value = resultSet.getTimestamp(index);\n");
            source.append("        return value == null ? null : value.toLocalDateTime();\n");
            source.append("    }\n");
        }
        if (types.contains("java.time.Instant")) {
            source.append("\n");
            source.append("    private static java.time.Instant readInstant(java.sql.ResultSet resultSet, int index) throws java.sql.SQLException {\n");
            source.append("        java.sql.Timestamp value = resultSet.getTimestamp(index);\n");
            source.append("        return value == null ? null : value.toInstant();\n");
            source.append("    }\n");
        }
    }

    private String classLiteral(String javaType) {
        return javaType + ".class";
    }

    private boolean isPrimitive(String javaType) {
        return Set.of("boolean", "byte", "short", "int", "long", "float", "double", "char").contains(javaType);
    }

    private String dummyValue(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "\"\"";
            case "java.lang.Long" -> "java.lang.Long.valueOf(0L)";
            case "long" -> "0L";
            case "java.lang.Integer" -> "java.lang.Integer.valueOf(0)";
            case "int" -> "0";
            case "java.lang.Boolean" -> "java.lang.Boolean.FALSE";
            case "boolean" -> "false";
            case "java.math.BigDecimal" -> "java.math.BigDecimal.ZERO";
            case "java.time.LocalDate" -> "java.time.LocalDate.of(1970, 1, 1)";
            case "java.time.LocalDateTime" -> "java.time.LocalDateTime.of(1970, 1, 1, 0, 0)";
            case "java.time.Instant" -> "java.time.Instant.EPOCH";
            default -> "new " + javaType + "()";
        };
    }

    private String sqlType(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "java.sql.Types.VARCHAR";
            case "java.lang.Long", "long" -> "java.sql.Types.BIGINT";
            case "java.lang.Integer", "int" -> "java.sql.Types.INTEGER";
            case "java.lang.Boolean", "boolean" -> "java.sql.Types.BOOLEAN";
            case "java.math.BigDecimal" -> "java.sql.Types.NUMERIC";
            case "java.time.LocalDate" -> "java.sql.Types.DATE";
            case "java.time.LocalDateTime", "java.time.Instant" -> "java.sql.Types.TIMESTAMP";
            default -> "java.sql.Types.JAVA_OBJECT";
        };
    }

    private String renderMetadata(List<EntityMetadata> metadata) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\n");
        json.append("  \"format\": \"mortar-metadata-v1\",\n");
        json.append("  \"entities\": [\n");
        for (int entityIndex = 0; entityIndex < metadata.size(); entityIndex++) {
            EntityMetadata entity = metadata.get(entityIndex);
            json.append("    {\n");
            json.append("      \"java_type\": \"").append(escapeJson(entity.javaType())).append("\",\n");
            json.append("      \"table\": \"").append(escapeJson(entity.tableName())).append("\",\n");
            json.append("      \"alias\": \"").append(escapeJson(entity.alias())).append("\",\n");
            json.append("      \"columns\": [\n");
            for (int columnIndex = 0; columnIndex < entity.columns().size(); columnIndex++) {
                ColumnModel column = entity.columns().get(columnIndex);
                json.append("        {\n");
                json.append("          \"property\": \"").append(escapeJson(column.propertyName())).append("\",\n");
                json.append("          \"column\": \"").append(escapeJson(column.columnName())).append("\",\n");
                json.append("          \"java_type\": \"").append(escapeJson(column.javaType())).append("\"\n");
                json.append("        }");
                if (columnIndex < entity.columns().size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      ],\n");
            if (entity.relations().isEmpty()) {
                json.append("      \"relations\": []\n");
            } else {
                json.append("      \"relations\": [\n");
                for (int relationIndex = 0; relationIndex < entity.relations().size(); relationIndex++) {
                    RelationModel relation = entity.relations().get(relationIndex);
                    json.append("        {\n");
                    json.append("          \"property\": \"").append(escapeJson(relation.propertyName())).append("\",\n");
                    json.append("          \"local_column\": \"").append(escapeJson(relation.localColumn())).append("\",\n");
                json.append("          \"target_table\": \"").append(escapeJson(relation.targetTable())).append("\",\n");
                json.append("          \"target_alias\": \"").append(escapeJson(relation.targetAlias())).append("\",\n");
                json.append("          \"target_column\": \"").append(escapeJson(relation.targetColumn())).append("\",\n");
                json.append("          \"nullable\": ").append(relation.nullable()).append("\n");
                json.append("        }");
                    if (relationIndex < entity.relations().size() - 1) {
                        json.append(",");
                    }
                    json.append("\n");
                }
                json.append("      ]\n");
            }
            json.append("    }");
            if (entityIndex < metadata.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String defaultAlias(String entityName) {
        return entityName.substring(0, 1).toLowerCase(Locale.ROOT);
    }

    private boolean isMortarEntity(TypeElement entity) {
        return entity.getAnnotation(MortarEntity.class) != null;
    }

    private String tableName(TypeElement entity) {
        MortarEntity metadata = entity.getAnnotation(MortarEntity.class);
        if (metadata != null) {
            return metadata.table();
        }

        return annotationValue(entity, "jakarta.persistence.Table", "name")
            .orElse(entity.getSimpleName().toString().toLowerCase(Locale.ROOT));
    }

    private String alias(TypeElement entity, String entityName) {
        MortarEntity metadata = entity.getAnnotation(MortarEntity.class);
        if (metadata != null && !metadata.alias().isBlank()) {
            return metadata.alias();
        }

        return defaultAlias(entityName);
    }

    private boolean isId(VariableElement field) {
        return field.getAnnotation(MortarId.class) != null || hasAnnotation(field, "jakarta.persistence.Id");
    }

    private Optional<String> columnName(VariableElement field) {
        MortarColumn column = field.getAnnotation(MortarColumn.class);
        if (column != null) {
            return Optional.of(column.name());
        }

        return annotationValue(field, "jakarta.persistence.Column", "name");
    }

    private Optional<String> relationLocalColumn(VariableElement field) {
        MortarRelation relation = field.getAnnotation(MortarRelation.class);
        if (relation != null) {
            return Optional.of(relation.localColumn());
        }

        return annotationValue(field, "jakarta.persistence.JoinColumn", "name");
    }

    private Optional<String> relationTargetColumn(VariableElement field) {
        MortarRelation relation = field.getAnnotation(MortarRelation.class);
        if (relation != null) {
            return Optional.of(relation.targetColumn());
        }

        return annotationValue(field, "jakarta.persistence.JoinColumn", "referencedColumnName");
    }

    private boolean relationNullable(VariableElement field) {
        MortarRelation relation = field.getAnnotation(MortarRelation.class);
        if (relation != null) {
            return relation.nullable();
        }

        return true;
    }

    private TypeElement relationTarget(VariableElement field) {
        if (field.asType() instanceof DeclaredType declaredType
            && declaredType.asElement() instanceof TypeElement target) {
            return target;
        }
        throw new IllegalStateException("Relation field must be a declared type: " + field.getSimpleName());
    }

    private boolean hasAnnotation(Element element, String annotationName) {
        return element.getAnnotationMirrors().stream()
            .anyMatch(annotation -> annotation.getAnnotationType().toString().equals(annotationName));
    }

    private Optional<String> annotationValue(Element element, String annotationName, String memberName) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            if (annotation.getAnnotationType().toString().equals(annotationName)) {
                for (java.util.Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : annotation.getElementValues().entrySet()) {
                    if (entry.getKey().getSimpleName().contentEquals(memberName)) {
                        return Optional.of(entry.getValue().getValue().toString());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String constantName(String entityName) {
        return entityName.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private record ColumnModel(String propertyName, String columnName, String javaType, boolean id) {
    }

    private record RelationModel(
        String propertyName,
        String localColumn,
        String targetTable,
        String targetAlias,
        String targetColumn,
        boolean nullable
    ) {
    }

    private record EntityMetadata(
        String javaType,
        String tableName,
        String alias,
        List<ColumnModel> columns,
        List<RelationModel> relations
    ) {
    }
}
