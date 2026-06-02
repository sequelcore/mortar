use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;
use thiserror::Error;

const SQL_SNAPSHOT_FORMAT: &str = "mortar-sql-snapshot-v1";
const MORTAR_METADATA_FORMAT: &str = "mortar-metadata-v1";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct QueryInspection {
    pub sql: String,
    pub tables: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotFile {
    pub format: String,
    pub snapshots: Vec<SqlSnapshot>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshot {
    pub name: String,
    pub sql: String,
    pub parameters: Vec<SqlSnapshotParameter>,
    pub metadata: SqlSnapshotMetadata,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotParameter {
    pub position: u32,
    pub java_type: String,
    pub value: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct SqlSnapshotMetadata {
    pub tables: Vec<String>,
    pub columns: Vec<String>,
    pub joins: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarMetadataFile {
    pub format: String,
    pub entities: Vec<MortarEntityMetadata>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarEntityMetadata {
    pub java_type: String,
    pub table: String,
    pub alias: String,
    pub columns: Vec<MortarColumnMetadata>,
    pub relations: Vec<MortarRelationMetadata>,
    #[serde(default)]
    pub queries: Vec<MortarQueryMetadata>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarColumnMetadata {
    pub property: String,
    pub column: String,
    pub java_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarRelationMetadata {
    pub property: String,
    pub local_column: String,
    pub target_table: String,
    pub target_alias: String,
    pub target_column: String,
    #[serde(default)]
    pub nullable: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQueryMetadata {
    pub id: String,
    pub name: String,
    pub shape: String,
    pub generated_source: MortarQuerySourceMetadata,
    pub parameters: Vec<MortarQueryParameterMetadata>,
    pub row_type: String,
    pub snapshot: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQuerySourceMetadata {
    pub java_type: String,
    pub member: String,
    pub generated_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct MortarQueryParameterMetadata {
    pub name: String,
    pub java_type: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DatabaseSchema {
    pub tables: Vec<DatabaseTable>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DatabaseTable {
    pub name: String,
    pub columns: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SchemaDrift {
    pub table: String,
    pub column: Option<String>,
    pub message: String,
}

#[derive(Debug, Error)]
pub enum MortarCompilerError {
    #[error("SQL cannot be blank")]
    BlankSql,
    #[error("Snapshot format must be mortar-sql-snapshot-v1")]
    InvalidSnapshotFormat,
    #[error("Snapshot name cannot be blank")]
    BlankSnapshotName,
    #[error("Snapshot SQL cannot be blank")]
    BlankSnapshotSql,
    #[error("Duplicate snapshot name: {0}")]
    DuplicateSnapshotName(String),
    #[error("Snapshot JSON is invalid: {0}")]
    InvalidSnapshotJson(#[from] serde_json::Error),
    #[error("Mortar metadata format must be mortar-metadata-v1")]
    InvalidMetadataFormat,
    #[error("Mortar metadata JSON is invalid: {0}")]
    InvalidMetadataJson(serde_json::Error),
}

pub fn inspect_sql(sql: &str) -> Result<QueryInspection, MortarCompilerError> {
    if sql.trim().is_empty() {
        return Err(MortarCompilerError::BlankSql);
    }

    Ok(QueryInspection {
        sql: sql.trim().to_string(),
        tables: Vec::new(),
    })
}

pub fn postgres_explain_sql(sql: &str) -> Result<String, MortarCompilerError> {
    let inspection = inspect_sql(sql)?;
    Ok(format!("explain (format text) {}", inspection.sql))
}

pub fn redact_parameter_value(parameter_name: &str, value: &str) -> String {
    let normalized = parameter_name.to_lowercase();
    if [
        "password",
        "passwd",
        "secret",
        "token",
        "api_key",
        "authorization",
    ]
    .iter()
    .any(|term| normalized.contains(term))
    {
        "[redacted]".to_string()
    } else {
        value.to_string()
    }
}

pub fn redact_connection_string(connection: &str) -> String {
    let without_password = redact_userinfo_password(connection);
    redact_query_passwords(&without_password)
}

fn redact_userinfo_password(connection: &str) -> String {
    let Some(scheme_end) = connection.find("://") else {
        return connection.to_string();
    };
    let authority_start = scheme_end + 3;
    let authority_end = connection[authority_start..]
        .find('/')
        .map(|index| authority_start + index)
        .unwrap_or(connection.len());
    let authority = &connection[authority_start..authority_end];
    let Some(at_index) = authority.rfind('@') else {
        return connection.to_string();
    };
    let userinfo = &authority[..at_index];
    let Some(password_start) = userinfo.find(':') else {
        return connection.to_string();
    };

    format!(
        "{}***{}",
        &connection[..authority_start + password_start + 1],
        &connection[authority_start + at_index..]
    )
}

fn redact_query_passwords(connection: &str) -> String {
    let Some(query_start) = connection.find('?') else {
        return connection.to_string();
    };
    let prefix = &connection[..query_start + 1];
    let query = &connection[query_start + 1..];
    let redacted = query
        .split('&')
        .map(|part| {
            let Some((name, _value)) = part.split_once('=') else {
                return part.to_string();
            };
            let lower = name.to_lowercase();
            if lower.contains("password") || lower.contains("token") || lower.contains("secret") {
                format!("{name}=***")
            } else {
                part.to_string()
            }
        })
        .collect::<Vec<_>>()
        .join("&");
    format!("{prefix}{redacted}")
}

pub fn parse_sql_snapshot_file(content: &str) -> Result<SqlSnapshotFile, MortarCompilerError> {
    let snapshot_file: SqlSnapshotFile = serde_json::from_str(content)?;
    validate_sql_snapshot_file(&snapshot_file)?;
    Ok(snapshot_file)
}

pub fn render_sql_snapshot_file(
    snapshot_file: &SqlSnapshotFile,
) -> Result<String, MortarCompilerError> {
    validate_sql_snapshot_file(snapshot_file)?;

    let mut canonical = snapshot_file.clone();
    canonical
        .snapshots
        .sort_by(|left, right| left.name.cmp(&right.name));

    Ok(format!("{}\n", serde_json::to_string_pretty(&canonical)?))
}

pub fn sql_snapshot_format() -> &'static str {
    SQL_SNAPSHOT_FORMAT
}

pub fn empty_sql_snapshot_file() -> SqlSnapshotFile {
    SqlSnapshotFile {
        format: SQL_SNAPSHOT_FORMAT.to_string(),
        snapshots: Vec::new(),
    }
}

pub fn parse_mortar_metadata_file(
    content: &str,
) -> Result<MortarMetadataFile, MortarCompilerError> {
    let metadata: MortarMetadataFile =
        serde_json::from_str(content).map_err(MortarCompilerError::InvalidMetadataJson)?;
    if metadata.format != MORTAR_METADATA_FORMAT {
        return Err(MortarCompilerError::InvalidMetadataFormat);
    }
    Ok(metadata)
}

pub fn detect_schema_drift(
    metadata: &MortarMetadataFile,
    database_schema: &DatabaseSchema,
) -> Vec<SchemaDrift> {
    let mut drift = Vec::new();
    for entity in &metadata.entities {
        let Some(table) = database_schema
            .tables
            .iter()
            .find(|table| table.name == entity.table)
        else {
            drift.push(SchemaDrift {
                table: entity.table.clone(),
                column: None,
                message: format!("Missing database table: {}", entity.table),
            });
            continue;
        };

        for column in &entity.columns {
            if !table.columns.contains(&column.column) {
                drift.push(SchemaDrift {
                    table: entity.table.clone(),
                    column: Some(column.column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        entity.table, column.column
                    ),
                });
            }
        }

        for relation in &entity.relations {
            if !table.columns.contains(&relation.local_column) {
                drift.push(SchemaDrift {
                    table: entity.table.clone(),
                    column: Some(relation.local_column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        entity.table, relation.local_column
                    ),
                });
            }

            let Some(target_table) = database_schema
                .tables
                .iter()
                .find(|table| table.name == relation.target_table)
            else {
                drift.push(SchemaDrift {
                    table: relation.target_table.clone(),
                    column: None,
                    message: format!("Missing database table: {}", relation.target_table),
                });
                continue;
            };

            if !target_table.columns.contains(&relation.target_column) {
                drift.push(SchemaDrift {
                    table: relation.target_table.clone(),
                    column: Some(relation.target_column.clone()),
                    message: format!(
                        "Missing database column: {}.{}",
                        relation.target_table, relation.target_column
                    ),
                });
            }
        }
    }
    drift
}

fn validate_sql_snapshot_file(snapshot_file: &SqlSnapshotFile) -> Result<(), MortarCompilerError> {
    if snapshot_file.format != SQL_SNAPSHOT_FORMAT {
        return Err(MortarCompilerError::InvalidSnapshotFormat);
    }

    let mut names = BTreeSet::new();
    for snapshot in &snapshot_file.snapshots {
        if snapshot.name.trim().is_empty() {
            return Err(MortarCompilerError::BlankSnapshotName);
        }
        if snapshot.sql.trim().is_empty() {
            return Err(MortarCompilerError::BlankSnapshotSql);
        }
        if !names.insert(snapshot.name.as_str()) {
            return Err(MortarCompilerError::DuplicateSnapshotName(
                snapshot.name.clone(),
            ));
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::{
        DatabaseSchema, DatabaseTable, MortarCompilerError, SqlSnapshot, SqlSnapshotFile,
        SqlSnapshotMetadata, SqlSnapshotParameter, detect_schema_drift, inspect_sql,
        parse_mortar_metadata_file, parse_sql_snapshot_file, postgres_explain_sql,
        redact_connection_string, redact_parameter_value, render_sql_snapshot_file,
        sql_snapshot_format,
    };

    #[test]
    fn rejects_blank_sql() {
        let error = inspect_sql("   ").expect_err("blank SQL should fail");

        assert!(matches!(error, MortarCompilerError::BlankSql));
    }

    #[test]
    fn trims_sql_for_inspection() {
        let inspection = inspect_sql(" select 1 ").expect("SQL should be inspected");

        assert_eq!(inspection.sql, "select 1");
        assert!(inspection.tables.is_empty());
    }

    #[test]
    fn builds_postgres_explain_sql() {
        let explain_sql = postgres_explain_sql(" select 1 ").expect("SQL should build");

        assert_eq!(explain_sql, "explain (format text) select 1");
    }

    #[test]
    fn redacts_sensitive_parameter_values() {
        assert_eq!(
            redact_parameter_value("password", "secret-value"),
            "[redacted]"
        );
        assert_eq!(redact_parameter_value("clientName", "Ada"), "Ada");
    }

    #[test]
    fn redacts_connection_string_passwords() {
        assert_eq!(
            redact_connection_string("postgres://app:secret@localhost:5432/app?sslmode=require"),
            "postgres://app:***@localhost:5432/app?sslmode=require"
        );
        assert_eq!(
            redact_connection_string("postgres://localhost/app?password=secret&sslmode=require"),
            "postgres://localhost/app?password=***&sslmode=require"
        );
    }

    #[test]
    fn renders_canonical_sql_snapshot_file() {
        let snapshot_file = SqlSnapshotFile {
            format: sql_snapshot_format().to_string(),
            snapshots: vec![
                SqlSnapshot {
                    name: "ClientRepository.findByName".to_string(),
                    sql: "select c.id from clients c where c.name = ?".to_string(),
                    parameters: vec![SqlSnapshotParameter {
                        position: 1,
                        java_type: "java.lang.String".to_string(),
                        value: Some("Ana".to_string()),
                    }],
                    metadata: SqlSnapshotMetadata {
                        tables: vec!["clients".to_string()],
                        columns: vec!["clients.id".to_string(), "clients.name".to_string()],
                        joins: Vec::new(),
                    },
                },
                SqlSnapshot {
                    name: "ClientRepository.count".to_string(),
                    sql: "select count(*) from clients c".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: vec!["clients".to_string()],
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
            ],
        };

        let rendered = render_sql_snapshot_file(&snapshot_file).expect("snapshot should render");

        assert_eq!(
            rendered,
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.count",
      "sql": "select count(*) from clients c",
      "parameters": [],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [],
        "joins": []
      }
    },
    {
      "name": "ClientRepository.findByName",
      "sql": "select c.id from clients c where c.name = ?",
      "parameters": [
        {
          "position": 1,
          "java_type": "java.lang.String",
          "value": "Ana"
        }
      ],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [
          "clients.id",
          "clients.name"
        ],
        "joins": []
      }
    }
  ]
}
"#
        );
    }

    #[test]
    fn parses_sql_snapshot_file() {
        let parsed = parse_sql_snapshot_file(
            r#"{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [
        {
          "position": 1,
          "java_type": "java.lang.Long",
          "value": "7"
        }
      ],
      "metadata": {
        "tables": [
          "clients"
        ],
        "columns": [
          "clients.id"
        ],
        "joins": []
      }
    }
  ]
}"#,
        )
        .expect("snapshot file should parse");

        assert_eq!(parsed.snapshots[0].name, "ClientRepository.findById");
        assert_eq!(
            parsed.snapshots[0].parameters[0].java_type,
            "java.lang.Long"
        );
    }

    #[test]
    fn rejects_invalid_sql_snapshot_format() {
        let error = parse_sql_snapshot_file(
            r#"{
  "format": "other",
  "snapshots": []
}"#,
        )
        .expect_err("invalid format should fail");

        assert!(matches!(error, MortarCompilerError::InvalidSnapshotFormat));
    }

    #[test]
    fn rejects_duplicate_sql_snapshot_names() {
        let snapshot_file = SqlSnapshotFile {
            format: sql_snapshot_format().to_string(),
            snapshots: vec![
                SqlSnapshot {
                    name: "ClientRepository.find".to_string(),
                    sql: "select 1".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: Vec::new(),
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
                SqlSnapshot {
                    name: "ClientRepository.find".to_string(),
                    sql: "select 2".to_string(),
                    parameters: Vec::new(),
                    metadata: SqlSnapshotMetadata {
                        tables: Vec::new(),
                        columns: Vec::new(),
                        joins: Vec::new(),
                    },
                },
            ],
        };

        let error =
            render_sql_snapshot_file(&snapshot_file).expect_err("duplicate names should fail");

        assert!(matches!(
            error,
            MortarCompilerError::DuplicateSnapshotName(name) if name == "ClientRepository.find"
        ));
    }

    #[test]
    fn parses_mortar_metadata_file() {
        let metadata = super::parse_mortar_metadata_file(
            r#"{
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
        }
      ],
      "relations": []
    }
  ]
}"#,
        )
        .expect("metadata should parse");

        assert_eq!(metadata.entities[0].java_type, "example.Client");
        assert_eq!(metadata.entities[0].columns[0].column, "id");
        assert!(metadata.entities[0].queries.is_empty());
    }

    #[test]
    fn parses_query_generated_source_metadata() {
        let metadata = super::parse_mortar_metadata_file(
            r#"{
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
        }
      ],
      "relations": [],
      "queries": [
        {
          "id": "example.Client.findById",
          "name": "findById",
          "shape": "findById",
          "generated_source": {
            "java_type": "example.QClient",
            "member": "findById",
            "generated_type": "example.QClient.FindByIdQuery"
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
}"#,
        )
        .expect("query metadata should parse");

        let query = &metadata.entities[0].queries[0];
        assert_eq!(query.id, "example.Client.findById");
        assert_eq!(query.generated_source.java_type, "example.QClient");
        assert_eq!(query.parameters[0].name, "id");
    }

    #[test]
    fn detects_schema_drift_for_missing_table_and_column() {
        let metadata = parse_mortar_metadata_file(
            r#"{
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
      "relations": []
    },
    {
      "java_type": "example.Route",
      "table": "routes",
      "alias": "r",
      "columns": [],
      "relations": []
    }
  ]
}"#,
        )
        .expect("metadata should parse");
        let schema = DatabaseSchema {
            tables: vec![DatabaseTable {
                name: "clients".to_string(),
                columns: vec!["id".to_string()],
            }],
        };

        let drift = detect_schema_drift(&metadata, &schema);

        assert_eq!(drift.len(), 2);
        assert_eq!(drift[0].message, "Missing database column: clients.name");
        assert_eq!(drift[1].message, "Missing database table: routes");
    }

    #[test]
    fn detects_r17_schema_drift_cases_from_ticket_fixture_metadata() {
        let metadata = parse_mortar_metadata_file(
            r#"{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "dev.mortar.examples.querycorpus.postgres.TicketRecord",
      "table": "tickets",
      "alias": "t",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        },
        {
          "property": "summary",
          "column": "summary",
          "java_type": "java.lang.String"
        },
        {
          "property": "priority",
          "column": "priority",
          "java_type": "java.lang.String"
        },
        {
          "property": "openedOn",
          "column": "opened_on",
          "java_type": "java.time.LocalDate"
        }
      ],
      "relations": [
        {
          "property": "customer",
          "local_column": "customer_id",
          "target_table": "customers",
          "target_alias": "cu",
          "target_column": "id",
          "nullable": false
        },
        {
          "property": "assignedTechnician",
          "local_column": "assigned_technician_id",
          "target_table": "technicians",
          "target_alias": "te",
          "target_column": "id",
          "nullable": true
        },
        {
          "property": "status",
          "local_column": "status_code",
          "target_table": "ticket_statuses",
          "target_alias": "ts",
          "target_column": "code",
          "nullable": false
        }
      ]
    },
    {
      "java_type": "dev.mortar.examples.querycorpus.postgres.TechnicianRecord",
      "table": "technicians",
      "alias": "te",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        },
        {
          "property": "displayName",
          "column": "display_name",
          "java_type": "java.lang.String"
        },
        {
          "property": "region",
          "column": "region",
          "java_type": "java.lang.String"
        }
      ],
      "relations": []
    }
  ]
}"#,
        )
        .expect("R17 metadata should parse");
        let database_schema = DatabaseSchema {
            tables: vec![
                DatabaseTable {
                    name: "tickets".to_string(),
                    columns: vec![
                        "id".to_string(),
                        "summary".to_string(),
                        "priority".to_string(),
                        "opened_on".to_string(),
                    ],
                },
                DatabaseTable {
                    name: "customers".to_string(),
                    columns: vec!["id".to_string()],
                },
                DatabaseTable {
                    name: "technicians".to_string(),
                    columns: vec!["id".to_string(), "display_name".to_string()],
                },
                DatabaseTable {
                    name: "ticket_statuses".to_string(),
                    columns: vec![],
                },
            ],
        };

        let drift = detect_schema_drift(&metadata, &database_schema);

        let messages = drift
            .iter()
            .map(|drift| drift.message.as_str())
            .collect::<Vec<_>>();
        assert_eq!(drift.len(), 5);
        assert!(messages.contains(&"Missing database column: tickets.customer_id"));
        assert!(messages.contains(&"Missing database column: tickets.assigned_technician_id"));
        assert!(messages.contains(&"Missing database column: tickets.status_code"));
        assert!(messages.contains(&"Missing database column: ticket_statuses.code"));
        assert!(messages.contains(&"Missing database column: technicians.region"));
    }
}
